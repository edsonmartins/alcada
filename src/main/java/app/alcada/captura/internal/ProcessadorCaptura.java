package app.alcada.captura.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.captura.port.CanalSaida;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.captura.port.Minimizacao;
import app.alcada.captura.port.Minimizador;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Pipeline de captura (RFC-0001): normaliza → filtra relevância → minimiza →
 * extrai (gateway) → re-hidrata → resolve entidade → deduplica → classifica e
 * roteia. Roteamento é determinístico (INV-10): o modelo entrega classe; a
 * regra decide o destino. Toda transição gera trilha.
 */
@ApplicationScoped
public class ProcessadorCaptura {

    @ConfigProperty(name = "captura.mencao-bot", defaultValue = "@alcada")
    String mencaoBot;

    @ConfigProperty(name = "captura.dedup.limiar", defaultValue = "0.82")
    double limiarDedup;

    private final EntityManager em;
    private final Minimizador minimizador;
    private final Extrator extrator;
    private final Trilha trilha;
    private final CanalSaida canal;

    public ProcessadorCaptura(EntityManager em, Minimizador minimizador, Extrator extrator,
                              Trilha trilha, CanalSaida canal) {
        this.em = em;
        this.minimizador = minimizador;
        this.extrator = extrator;
        this.trilha = trilha;
        this.canal = canal;
    }

    @Transactional
    public void processar(OrgId org, UUID eventoBrutoId) {
        Object[] ev = eventoBruto(org, eventoBrutoId);
        if (ev == null) {
            return;
        }
        UUID fonteId = (UUID) ev[0];
        String texto = (String) ev[1];
        String autor = (String) ev[2];
        String thread = (String) ev[3];
        String tipoFonte = tipoFonte(org, fonteId);

        // 1. Relevância determinística (antes do modelo)
        if (!relevante(tipoFonte, texto)) {
            registrarDescarte(org, fonteId, "SEM_RELEVANCIA");
            return;
        }

        // 2. Minimização + extração + re-hidratação
        List<String> pessoas = new ArrayList<>();
        List<String> empresas = new ArrayList<>();
        entidadesConhecidas(org, pessoas, empresas);
        Minimizacao min = minimizador.minimizar(texto, pessoas, empresas);
        Optional<DadosExtraidos> dados = extrator.extrair(org, eventoBrutoId, min.textoMinimizado(), min::rehidratar);

        if (dados.isEmpty()) {
            // baixa confiança: item entra na fila com aviso, extração pendente
            UUID id = criarPendencia(org, resumo(texto), null, null, null, null,
                    "DECISAO", horizonte(null), "ENTRADA", null, true, null);
            gravarOrigem(org, id, tipoFonte, autor, thread);
            registrarTrilhaCriacao(org, id, fonteId, eventoBrutoId, tipoFonte, "ENTRADA", null);
            responder(org, autor, tipoFonte, id, eventoBrutoId, "recebido (extração pendente)");
            return;
        }

        DadosExtraidos d = dados.get();

        // 3. Resolução de entidade
        UUID entidadeId = resolverEntidade(org, d);

        // 4. Deduplicação (mesma entidade + janela 7d + similaridade > limiar)
        Optional<UUID> existente = deduplicar(org, entidadeId, d);
        if (existente.isPresent()) {
            criarCobranca(org, existente.get(), eventoBrutoId);
            em.createNativeQuery("UPDATE pendencia SET temperatura = temperatura + 1 WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, existente.get()).executeUpdate();
            trilha.registrar(new EventoTrilha(org, existente.get(), TipoEvento.FUNDIDA,
                    Ator.sistemaMotor("captura"), null, null, origem(fonteId, eventoBrutoId, tipoFonte), null));
            responder(org, autor, tipoFonte, existente.get(), eventoBrutoId, "já registrado; estado atualizado");
            return;
        }

        // 5. Classificação + roteamento determinístico
        Optional<Object[]> regra = regraAtiva(org, d.classeSugerida());
        if (regra.isPresent()) {
            UUID regraId = (UUID) regra.get()[0];
            String nivel = (String) regra.get()[1];
            UUID id = criarPendencia(org, d.titulo(), d.quemEspera(), d.oQueTrava(), d.prazoImplicito(),
                    d.valorEmJogo(), d.classeSugerida(), horizonte(d.prazoImplicito()), "DELEGADA",
                    entidadeId, false, d.confianca());
            gravarOrigem(org, id, tipoFonte, autor, thread);
            trilha.registrar(new EventoTrilha(org, id, TipoEvento.ROTEADA_POR_REGRA,
                    Ator.sistemaRegra(regraId.toString()), null, "DELEGADA",
                    origem(fonteId, eventoBrutoId, tipoFonte), "{\"nivel\":\"" + nivel + "\"}"));
            responder(org, autor, tipoFonte, id, eventoBrutoId, "encaminhado automaticamente");
            return;
        }

        UUID id = criarPendencia(org, d.titulo(), d.quemEspera(), d.oQueTrava(), d.prazoImplicito(),
                d.valorEmJogo(), d.classeSugerida(), horizonte(d.prazoImplicito()), "ENTRADA",
                entidadeId, false, d.confianca());
        gravarOrigem(org, id, tipoFonte, autor, thread);
        registrarTrilhaCriacao(org, id, fonteId, eventoBrutoId, tipoFonte, "ENTRADA", d.confianca());
        responder(org, autor, tipoFonte, id, eventoBrutoId, "na sua entrada");
    }

    // ---- passos -----------------------------------------------------------

    private boolean relevante(String tipoFonte, String texto) {
        if ("WEBHOOK".equals(tipoFonte)) {
            return true; // sistema declarado
        }
        return texto != null && texto.toLowerCase().contains(mencaoBot.toLowerCase());
    }

    private UUID resolverEntidade(OrgId org, DadosExtraidos d) {
        List<String> candidatos = new ArrayList<>();
        if (d.quemEspera() != null) {
            candidatos.add(d.quemEspera());
        }
        candidatos.addAll(d.entidades());
        for (String nome : candidatos) {
            try {
                UUID id = (UUID) em.createNativeQuery(
                        "SELECT id FROM entidade WHERE org_id = ? AND lower(nome_canonico) = lower(?)")
                        .setParameter(1, org.valor()).setParameter(2, nome).getSingleResult();
                return id;
            } catch (NoResultException ignorado) {
                // tenta o próximo
            }
        }
        // não encontrada: cria (o índice de apelidos evolui com confirmações do gestor)
        String nome = candidatos.isEmpty() ? "desconhecido" : candidatos.get(0);
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO entidade (id, org_id, tipo, nome_canonico) VALUES (?, ?, 'PESSOA', ?)")
                .setParameter(1, id).setParameter(2, org.valor()).setParameter(3, nome).executeUpdate();
        return id;
    }

    private Optional<UUID> deduplicar(OrgId org, UUID entidadeId, DadosExtraidos d) {
        @SuppressWarnings("unchecked")
        List<Object[]> abertas = em.createNativeQuery("""
                SELECT id, coalesce(titulo,'') || ' ' || coalesce(o_que_trava,'')
                FROM pendencia
                WHERE org_id = ? AND entidade_id = ? AND status <> 'FECHADA'
                  AND criada_em > now() - interval '7 days'
                """)
                .setParameter(1, org.valor()).setParameter(2, entidadeId).getResultList();

        UUID melhor = null;
        double melhorSim = 0;
        for (Object[] p : abertas) {
            double sim = Similaridade.jaccard(d.textoComparacao(), (String) p[1]);
            if (sim > melhorSim) {
                melhorSim = sim;
                melhor = (UUID) p[0];
            }
        }
        return melhorSim >= limiarDedup ? Optional.of(melhor) : Optional.empty();
    }

    private Optional<Object[]> regraAtiva(OrgId org, String classe) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT id, nivel FROM regra_autonomia WHERE org_id = ? AND classe = ? AND ativa LIMIT 1")
                    .setParameter(1, org.valor()).setParameter(2, classe).getSingleResult();
            return Optional.of(r);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    // ---- persistência auxiliar --------------------------------------------

    private Object[] eventoBruto(OrgId org, UUID id) {
        try {
            return (Object[]) em.createNativeQuery(
                    "SELECT fonte_id, texto, autor_ext, thread_ref FROM evento_bruto WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, id).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /** Denormaliza o endereço de retorno na pendência (006): o fechamento pode
     *  ocorrer depois do expurgo do bruto. */
    private void gravarOrigem(OrgId org, UUID pendenciaId, String canalFonte, String destino, String thread) {
        em.createNativeQuery("""
                UPDATE pendencia SET origem_canal = ?, origem_destino = ?, origem_thread = ?
                WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, canalFonte).setParameter(2, destino).setParameter(3, thread)
                .setParameter(4, org.valor()).setParameter(5, pendenciaId).executeUpdate();
    }

    private String tipoFonte(OrgId org, UUID fonteId) {
        return (String) em.createNativeQuery("SELECT tipo FROM fonte WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, fonteId).getSingleResult();
    }

    private void entidadesConhecidas(OrgId org, List<String> pessoas, List<String> empresas) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT tipo, nome_canonico FROM entidade WHERE org_id = ?")
                .setParameter(1, org.valor()).getResultList();
        for (Object[] l : linhas) {
            if ("EMPRESA".equals(l[0])) {
                empresas.add((String) l[1]);
            } else {
                pessoas.add((String) l[1]);
            }
        }
    }

    private UUID criarPendencia(OrgId org, String titulo, String quemEspera, String oQueTrava,
                                String prazoIso, java.math.BigDecimal valor, String classe,
                                String horizonte, String status, UUID entidadeId,
                                boolean baixaConfianca, Double confianca) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO pendencia
                    (id, org_id, titulo, quem_espera, o_que_trava, prazo_implicito, valor_em_jogo,
                     classe, horizonte, status, entidade_id, confianca, baixa_confianca)
                VALUES (?, ?, ?, ?, ?, cast(? as timestamptz), ?, ?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, id).setParameter(2, org.valor())
                .setParameter(3, titulo).setParameter(4, quemEspera).setParameter(5, oQueTrava)
                .setParameter(6, prazoIso).setParameter(7, valor)
                .setParameter(8, classe).setParameter(9, horizonte).setParameter(10, status)
                .setParameter(11, entidadeId).setParameter(12, confianca).setParameter(13, baixaConfianca)
                .executeUpdate();
        return id;
    }

    private void criarCobranca(OrgId org, UUID pendenciaId, UUID eventoBrutoId) {
        em.createNativeQuery(
                "INSERT INTO cobranca (org_id, pendencia_id, evento_bruto_id) VALUES (?, ?, ?)")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).setParameter(3, eventoBrutoId)
                .executeUpdate();
    }

    private void registrarDescarte(OrgId org, UUID fonteId, String motivo) {
        em.createNativeQuery(
                "INSERT INTO descarte_captura (org_id, fonte_id, motivo) VALUES (?, ?, ?)")
                .setParameter(1, org.valor()).setParameter(2, fonteId).setParameter(3, motivo)
                .executeUpdate();
    }

    private void registrarTrilhaCriacao(OrgId org, UUID pendenciaId, UUID fonteId, UUID eventoBrutoId,
                                        String tipoFonte, String estadoPosterior, Double confianca) {
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.CAPTADA,
                Ator.sistemaMotor("captura"), null, estadoPosterior,
                origem(fonteId, eventoBrutoId, tipoFonte), null));
    }

    private void responder(OrgId org, String autor, String canalNome, UUID pendenciaId,
                           UUID eventoBrutoId, String estado) {
        canal.responder(org, pendenciaId, new EnviarMensagem(canalNome, autor,
                "Pendência " + pendenciaId + ": " + estado, null,
                eventoBrutoId + ":resposta"));
    }

    /** Origem só com referências por id — nunca identificador direto (ADR-0016). */
    private static String origem(UUID fonteId, UUID eventoBrutoId, String canal) {
        return "{\"canal\":\"" + canal + "\",\"fonte_id\":\"" + fonteId
                + "\",\"evento_bruto_id\":\"" + eventoBrutoId + "\"}";
    }

    private static String resumo(String texto) {
        if (texto == null || texto.isBlank()) {
            return "(sem texto)";
        }
        String t = texto.strip();
        return t.length() <= 80 ? t : t.substring(0, 80) + "…";
    }

    private static String horizonte(String prazoIso) {
        if (prazoIso == null) {
            return "SEMANA";
        }
        try {
            OffsetDateTime prazo = OffsetDateTime.parse(prazoIso);
            OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
            if (!prazo.isAfter(agora.plusDays(1))) {
                return "HOJE";
            }
            return prazo.isAfter(agora.plusDays(7)) ? "TRIMESTRE" : "SEMANA";
        } catch (Exception e) {
            return "SEMANA";
        }
    }
}
