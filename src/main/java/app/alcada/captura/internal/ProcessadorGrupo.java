package app.alcada.captura.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 * Extração por JANELA de conversa de grupo (024, F2): monta as últimas N mensagens
 * do grupo (remetente por linha), minimiza (ADR-0020 §3), chama {@link ExtratorGrupo}
 * e, se há algo que <b>depende do gestor</b>, cria uma pendência na Entrada. Se já
 * há uma pendência aberta do mesmo grupo, é <b>cobrança</b>: esquenta e funde (não
 * duplica). O modelo propõe; o código executa o conjunto fechado (INV-10). Ator da
 * trilha = {@code ASSISTENTE} (INV-11). Só o fato é persistido — não a transcrição.
 */
@ApplicationScoped
public class ProcessadorGrupo {

    static final String MODELO = "extrator-grupo";
    static final String VERSAO = "v1";

    @ConfigProperty(name = "grupos.janela", defaultValue = "20")
    int janelaMax;

    @ConfigProperty(name = "grupos.confianca-min", defaultValue = "0.4")
    double confMin;

    @ConfigProperty(name = "grupos.cobranca-escala", defaultValue = "2")
    long limiarEscala;

    @ConfigProperty(name = "grupos.descarte-limiar", defaultValue = "2")
    long limiarDescarte;

    private final EntityManager em;
    private final Minimizador minimizador;
    private final PreFiltroGrupo preFiltro;
    private final ExtratorGrupo extrator;
    private final Trilha trilha;

    public ProcessadorGrupo(EntityManager em, Minimizador minimizador, PreFiltroGrupo preFiltro,
                            ExtratorGrupo extrator, Trilha trilha) {
        this.em = em;
        this.minimizador = minimizador;
        this.preFiltro = preFiltro;
        this.extrator = extrator;
        this.trilha = trilha;
    }

    @Transactional
    public void processar(OrgId org, String grupoId) {
        @SuppressWarnings("unchecked")
        List<Object[]> msgs = em.createNativeQuery("""
                SELECT id, autor_ext, texto, fonte_id FROM evento_bruto
                WHERE org_id = ? AND grupo AND thread_ref = ?
                ORDER BY recebido_em DESC LIMIT ?
                """)
                .setParameter(1, org.valor()).setParameter(2, grupoId).setParameter(3, janelaMax)
                .getResultList();
        if (msgs.isEmpty()) {
            return;
        }
        Collections.reverse(msgs); // ordem cronológica
        UUID ultimoId = (UUID) msgs.get(msgs.size() - 1)[0];
        UUID fonteId = (UUID) msgs.get(0)[3];

        StringBuilder janela = new StringBuilder();
        for (Object[] m : msgs) {
            janela.append(m[1] == null ? "?" : m[1]).append(": ")
                    .append(m[2] == null ? "" : m[2]).append('\n');
        }

        // Pré-filtro determinístico ANTES do modelo (ADR-0011 §3): ruído puro é
        // descartado sem chamar o gateway; a proporção processadas/vistas fica
        // registrada por fonte como evidência de captura seletiva.
        Optional<UUID> existente = pendenciaAbertaDoGrupo(org, grupoId);
        boolean candidata = preFiltro.candidata(janela.toString(), existente.isPresent());
        registrarProporcao(org, fonteId, candidata);
        if (!candidata) {
            return; // C2: ruído não vira nada e não vai ao modelo
        }

        List<String> pessoas = new ArrayList<>();
        List<String> empresas = new ArrayList<>();
        entidadesConhecidas(org, pessoas, empresas);
        Minimizacao min = minimizador.minimizar(janela.toString(), pessoas, empresas);

        Optional<Compromisso> c = extrator.extrair(org, ultimoId, min.textoMinimizado(), "o gestor", min::rehidratar);
        if (c.isEmpty()) {
            return; // indisponível/inválido: gateway já enfileirou reprocesso
        }
        Compromisso k = c.get();
        if (!k.dependeDoGestor() || k.confianca() < confMin) {
            return; // não é do gestor / baixa confiança → não cria item
        }

        if (existente.isPresent()) {
            UUID pid = existente.get();
            // Cobrança: não duplica — funde na pendência aberta, registra a cobrança
            // (rastro/contador e permite desfundir) e esquenta o item.
            em.createNativeQuery(
                    "INSERT INTO cobranca (org_id, pendencia_id, evento_bruto_id) VALUES (?, ?, ?)")
                    .setParameter(1, org.valor()).setParameter(2, pid).setParameter(3, ultimoId).executeUpdate();
            em.createNativeQuery("UPDATE pendencia SET temperatura = temperatura + 1 WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pid).executeUpdate();
            trilha.registrar(new EventoTrilha(org, pid, TipoEvento.FUNDIDA,
                    Ator.assistente(MODELO, VERSAO), null, null, origem(grupoId, ultimoId), null));
            // Ao cruzar o limiar, escala uma única vez (o evento marca a transição).
            if (contarCobrancas(org, pid) == limiarEscala) {
                trilha.registrar(new EventoTrilha(org, pid, TipoEvento.ESCALADA,
                        Ator.assistente(MODELO, VERSAO), null, null, origem(grupoId, ultimoId), null));
            }
            return;
        }

        UUID id = criarPendencia(org, k, grupoId);
        trilha.registrar(new EventoTrilha(org, id, TipoEvento.CAPTADA,
                Ator.assistente(MODELO, VERSAO), null, "ENTRADA", origem(grupoId, ultimoId), null));
    }

    private UUID criarPendencia(OrgId org, Compromisso k, String grupoId) {
        UUID id = UUID.randomUUID();
        // Descarte realimenta (011): grupo com descartes acima do limiar faz o novo
        // item nascer "rever" (baixa confiança) — nunca dropado. Espelha o 1:1: o
        // gestor treina, o filtro atenua, mas não deixa passar despercebido.
        boolean rever = grupoMarcadoParaRever(org, grupoId);
        em.createNativeQuery("""
                INSERT INTO pendencia
                    (id, org_id, titulo, quem_espera, o_que_trava, prazo_implicito, classe, horizonte,
                     status, confianca, baixa_confianca, origem_canal, origem_thread)
                VALUES (?, ?, ?, ?, ?, cast(? as timestamptz), 'DECISAO', ?, 'ENTRADA', ?, ?, 'WHATSAPP', ?)
                """)
                .setParameter(1, id).setParameter(2, org.valor())
                .setParameter(3, k.assunto()).setParameter(4, primeiroNome(k.quemPede()))
                .setParameter(5, k.acaoPendente())
                .setParameter(6, k.quandoResolvido()).setParameter(7, horizonte(k.quandoResolvido()))
                .setParameter(8, k.confianca()).setParameter(9, rever).setParameter(10, grupoId)
                .executeUpdate();
        return id;
    }

    /** Grupo cujo gestor já descartou itens acima do limiar → futuros nascem "rever". */
    private boolean grupoMarcadoParaRever(OrgId org, String grupoId) {
        long n = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM sinal_descarte WHERE org_id = ? AND chave = ?")
                .setParameter(1, org.valor()).setParameter(2, grupoId).getSingleResult()).longValue();
        return n >= limiarDescarte;
    }

    /**
     * Identidade mínima por finalidade (emenda ADR-0011, C8): o DONO é mostrado ao
     * gestor pelo <b>primeiro nome</b>. O contato completo, quando é a própria ação
     * ("mandar invite para fulano@..."), fica em {@code acaoPendente} — não aqui.
     */
    static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        String t = nome.trim();
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }

    /** Incrementa o contador auditável por fonte: toda janela vista, e as processadas. */
    private void registrarProporcao(OrgId org, UUID fonteId, boolean processada) {
        em.createNativeQuery("""
                INSERT INTO captura_proporcao (org_id, fonte_id, janelas_vistas, janelas_processadas)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (org_id, fonte_id) DO UPDATE
                    SET janelas_vistas = captura_proporcao.janelas_vistas + 1,
                        janelas_processadas = captura_proporcao.janelas_processadas + ?,
                        atualizado_em = now()
                """)
                .setParameter(1, org.valor()).setParameter(2, fonteId)
                .setParameter(3, processada ? 1 : 0).setParameter(4, processada ? 1 : 0)
                .executeUpdate();
    }

    private long contarCobrancas(OrgId org, UUID pendenciaId) {
        return ((Number) em.createNativeQuery(
                "SELECT count(*) FROM cobranca WHERE org_id = ? AND pendencia_id = ?")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult()).longValue();
    }

    /** Pendência aberta cuja origem é este grupo — para cobrança/dedup. */
    private Optional<UUID> pendenciaAbertaDoGrupo(OrgId org, String grupoId) {
        try {
            UUID id = (UUID) em.createNativeQuery("""
                    SELECT id FROM pendencia
                    WHERE org_id = ? AND origem_thread = ? AND status <> 'FECHADA'
                    ORDER BY criada_em DESC LIMIT 1
                    """).setParameter(1, org.valor()).setParameter(2, grupoId).getSingleResult();
            return Optional.of(id);
        } catch (NoResultException e) {
            return Optional.empty();
        }
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

    /** Origem só com referências por id — nunca identificador direto (ADR-0016). */
    private static String origem(String grupoId, UUID eventoBrutoId) {
        return "{\"canal\":\"WHATSAPP\",\"grupo\":true,\"evento_bruto_id\":\"" + eventoBrutoId + "\"}";
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
