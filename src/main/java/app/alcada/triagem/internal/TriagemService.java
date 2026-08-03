package app.alcada.triagem.internal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.FusoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

/**
 * Triagem (ADR-0002): as quatro saídas e o adiar como transições determinísticas
 * com trilha. `repassar` é do motor de autonomia (002). Só `FECHADA` notifica.
 */
@ApplicationScoped
public class TriagemService implements app.alcada.triagem.port.Triagem {

    static final String JOB_DESPERTAR = "TRIAGEM_DESPERTAR";
    private static final Set<String> MOTIVOS = Set.of("NADA", "INSUMO", "TERCEIRO");

    private final EntityManager em;
    private final Trilha trilha;
    private final Outbox outbox;
    private final Agenda agenda;
    private final FusoTenant fuso;

    /**
     * Janela entre o "sim" e o compromisso aparecer na agenda do gestor (INV-14).
     * Curta de propósito: a janela do motor (horas) serve para efeito que o
     * sistema executa por ausência; aqui o ato é deliberado e o gestor pode
     * querer ver o evento hoje mesmo.
     */
    @ConfigProperty(name = "alcada.calendario.janela", defaultValue = "PT5M")
    Duration janelaCompromisso;

    public TriagemService(EntityManager em, Trilha trilha, Outbox outbox, Agenda agenda,
                          FusoTenant fuso) {
        this.em = em;
        this.trilha = trilha;
        this.outbox = outbox;
        this.agenda = agenda;
        this.fuso = fuso;
    }

    // ---- saídas ------------------------------------------------------------

    @Override
    @Transactional
    public boolean emEntrada(OrgId org, UUID pendenciaId) {
        try {
            String status = (String) em.createNativeQuery(
                    "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
            return "ENTRADA".equals(status);
        } catch (NoResultException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public void resolver(OrgId org, UUID pendenciaId, String nota, UUID gestorId) {
        resolver(org, pendenciaId, nota, null, gestorId);
    }

    @Override
    @Transactional
    public void resolver(OrgId org, UUID pendenciaId, String nota, Lembrete lembrete, UUID gestorId) {
        exigirEntrada(org, pendenciaId);
        // Valida antes de fechar: lembrete inválido não deixa o item meio resolvido.
        if (lembrete != null) {
            lembrete.exigirUtil();
        }
        setStatus(org, pendenciaId, "FECHADA");
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.RESOLVIDA,
                Ator.humano(gestorId), "ENTRADA", "FECHADA", null, null));
        // só FECHADA notifica solicitante/contraparte (INV-09)
        outbox.publicar(new MensagemOutbox(org, "item.fechado",
                "{\"pendencia_id\":\"" + pendenciaId + "\"}", pendenciaId + ":fechado"));
        if (lembrete != null) {
            criarLembrete(org, pendenciaId, lembrete, gestorId);
        }
    }

    // ---- lembrete datado (RFC-0009) ----------------------------------------

    /**
     * O compromisso vira uma pendência nova, dormindo até a data: no dia ela
     * desperta na Entrada como qualquer outro item (fila única, INV-03). Não há
     * caixa de lembretes — até despertar, ele é invisível (ADR-0018).
     */
    private void criarLembrete(OrgId org, UUID origemId, Lembrete lembrete, UUID gestorId) {
        UUID lembreteId = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status, volta_em,
                                       ocorrencia, origem, origem_pendencia_id)
                VALUES (?, ?, ?, 'DECISAO', ?, 'DORMINDO', ?, 1, 'LEMBRETE', ?)
                """)
                .setParameter(1, lembreteId).setParameter(2, org.valor())
                .setParameter(3, lembrete.texto().trim())
                .setParameter(4, horizonteDe(org, lembrete.quando()))
                .setParameter(5, lembrete.quando()).setParameter(6, origemId)
                .executeUpdate();
        // O evento fica na pendência de ORIGEM: é lá que se lê "resolvi, e sobrou isto".
        trilha.registrar(new EventoTrilha(org, origemId, TipoEvento.LEMBRETE_CRIADO,
                Ator.humano(gestorId), null, null, null,
                "{\"lembrete_id\":\"" + lembreteId + "\",\"quando\":\"" + lembrete.quando() + "\"}"));
        agendarDespertar(org, lembreteId, 1, lembrete.quando());
        if (lembrete.comCalendario()) {
            agendarCompromisso(org, lembreteId, lembrete, gestorId);
        }
    }

    /**
     * O compromisso no calendário do gestor é efeito externo (RFC-0009): vai para
     * o outbox e só fica disponível depois da janela de arrependimento — desfazer
     * antes disso descarta a linha e a agenda nunca soube (INV-14).
     */
    private void agendarCompromisso(OrgId org, UUID lembreteId, Lembrete lembrete, UUID gestorId) {
        String payload = "{\"lembrete_id\":\"" + lembreteId + "\",\"gestor_id\":\"" + gestorId
                + "\",\"quando\":\"" + lembrete.quando() + "\",\"titulo\":"
                + jsonTexto(lembrete.texto().trim()) + "}";
        outbox.publicarApos(
                new MensagemOutbox(org, "EVENTO_CALENDARIO", payload,
                        app.alcada.triagem.port.Triagem.chaveCompromisso(lembreteId)),
                OffsetDateTime.now(ZoneOffset.UTC).plus(janelaCompromisso));
    }

    private static String jsonTexto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Horizonte pela distância até a data, no fuso do tenant (ADR-0008). */
    private String horizonteDe(OrgId org, OffsetDateTime quando) {
        java.time.ZoneId zona = fuso.fuso(org);
        java.time.LocalDate hoje = java.time.LocalDate.now(zona);
        java.time.LocalDate dia = quando.atZoneSameInstant(zona).toLocalDate();
        if (!dia.isAfter(hoje)) {
            return "HOJE";
        }
        return dia.isAfter(hoje.plusDays(7)) ? "TRIMESTRE" : "SEMANA";
    }

    /**
     * Descarte de 1 toque (001): fecha o item como ruído e realimenta o filtro —
     * grava um sinal pelo remetente para a captura marcar futuras capturas dele
     * como baixa confiança ("rever"), nunca dropando.
     */
    @Transactional
    public void descartar(OrgId org, UUID pendenciaId, UUID gestorId) {
        exigirEntrada(org, pendenciaId);
        String remetente = remetente(org, pendenciaId);
        setStatus(org, pendenciaId, "FECHADA");
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.DESCARTADA,
                Ator.humano(gestorId), "ENTRADA", "FECHADA", null, null));
        if (remetente != null && !remetente.isBlank()) {
            em.createNativeQuery(
                    "INSERT INTO sinal_descarte (org_id, chave, pendencia_id) VALUES (?, ?, ?)")
                    .setParameter(1, org.valor()).setParameter(2, remetente).setParameter(3, pendenciaId)
                    .executeUpdate();
        }
        // Descarte é silêncio: nada sai por outbox (não avisa o remetente).
    }

    /**
     * Chave de aprendizado do descarte. Em 1:1 é o remetente (origem_destino); em
     * grupo não há remetente único e origem_destino é nulo — cai no grupo
     * (origem_thread), sem tocar origem_destino (que dispararia envio ao canal).
     */
    private String remetente(OrgId org, UUID pendenciaId) {
        try {
            Object v = em.createNativeQuery(
                    "SELECT coalesce(origem_destino, origem_thread) FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
            return v == null ? null : v.toString();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public void reservar(OrgId org, UUID pendenciaId, OffsetDateTime agendadoPara, UUID gestorId) {
        exigirEntrada(org, pendenciaId);
        em.createNativeQuery("UPDATE pendencia SET status = 'AGENDADA', agendado_para = ? WHERE org_id = ? AND id = ?")
                .setParameter(1, agendadoPara).setParameter(2, org.valor()).setParameter(3, pendenciaId)
                .executeUpdate();
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.RESERVADA,
                Ator.humano(gestorId), "ENTRADA", "AGENDADA", null,
                "{\"agendado_para\":\"" + agendadoPara + "\"}"));
    }

    @Transactional
    public void repousar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, UUID gestorId) {
        exigirEntrada(org, pendenciaId);
        int ocorrencia = proximaOcorrencia(org, pendenciaId);
        em.createNativeQuery("""
                UPDATE pendencia SET status = 'DORMINDO', volta_em = ?, ocorrencia = ? WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, voltaEm).setParameter(2, ocorrencia)
                .setParameter(3, org.valor()).setParameter(4, pendenciaId).executeUpdate();
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.REPOUSADA,
                Ator.humano(gestorId), "ENTRADA", "DORMINDO", null,
                "{\"volta_em\":\"" + voltaEm + "\"}"));
        agendarDespertar(org, pendenciaId, ocorrencia, voltaEm);
    }

    /** Adiar (ADR-0002): fica em ENTRADA, mas com data e o_que_falta. Resposta diferenciada. */
    @Transactional
    public String adiar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, String oQueFalta, UUID gestorId) {
        if (!MOTIVOS.contains(oQueFalta)) {
            throw new FalhasTriagem.MotivoInvalido("o_que_falta inválido: " + oQueFalta);
        }
        exigirEntrada(org, pendenciaId);
        int ocorrencia = proximaOcorrencia(org, pendenciaId);
        em.createNativeQuery("""
                UPDATE pendencia SET adiado_count = adiado_count + 1, volta_em = ?, ocorrencia = ?
                WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, voltaEm).setParameter(2, ocorrencia)
                .setParameter(3, org.valor()).setParameter(4, pendenciaId).executeUpdate();
        em.createNativeQuery("""
                INSERT INTO adiamento (org_id, pendencia_id, volta_em, o_que_falta, ocorrencia)
                VALUES (?, ?, ?, ?, ?)
                """)
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).setParameter(3, voltaEm)
                .setParameter(4, oQueFalta).setParameter(5, ocorrencia).executeUpdate();
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.ADIADA,
                Ator.humano(gestorId), "ENTRADA", "ENTRADA", null,
                "{\"volta_em\":\"" + voltaEm + "\",\"o_que_falta\":\"" + oQueFalta + "\"}"));
        agendarDespertar(org, pendenciaId, ocorrencia, voltaEm);

        // Resposta diferenciada (ADR-0002)
        return switch (oQueFalta) {
            case "NADA" -> "bloco_decisao";     // não está bloqueado, está evitado
            case "TERCEIRO" -> "repassar";      // oferece repassar a quem tem a bola
            default -> "cobrar_insumo";         // INSUMO: passa a cobrar o insumo
        };
    }

    // ---- despertar (job) ---------------------------------------------------

    @Transactional
    public void aoDespertar(OrgId org, UUID pendenciaId, int ocorrencia) {
        Object[] p;
        try {
            p = (Object[]) em.createNativeQuery(
                    "SELECT status, ocorrencia FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        } catch (NoResultException e) {
            return;
        }
        String status = (String) p[0];
        int atual = ((Number) p[1]).intValue();
        if (atual != ocorrencia) {
            return; // despertar obsoleto (item foi re-adormecido)
        }
        if ("DORMINDO".equals(status)) {
            setStatus(org, pendenciaId, "ENTRADA");
        } else if (!"ENTRADA".equals(status)) {
            return; // já resolvido/delegado
        }
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.DESPERTADA,
                Ator.sistemaMotor("triagem"), status, "ENTRADA", null,
                "{\"ocorrencia\":" + ocorrencia + "}"));
    }

    // ---- Hoje (função de ordenação, PRODUTO §6) ----------------------------

    public record ItemHoje(String id, String titulo, String justificativa, String classe,
                           String quemEspera, String oQueTrava, Double valorEmJogo,
                           java.time.OffsetDateTime prazoImplicito, int temperatura) {
    }

    @Transactional
    public List<ItemHoje> hoje(OrgId org) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, valor_em_jogo, prazo_implicito, temperatura,
                       classe, quem_espera, o_que_trava
                FROM pendencia
                WHERE org_id = ? AND status = 'ENTRADA'
                ORDER BY (valor_em_jogo IS NOT NULL) DESC, valor_em_jogo DESC NULLS LAST,
                         (prazo_implicito IS NOT NULL) DESC, prazo_implicito ASC NULLS LAST,
                         temperatura DESC, criada_em ASC
                LIMIT 3
                """)
                .setParameter(1, org.valor()).getResultList();

        List<ItemHoje> hoje = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            hoje.add(new ItemHoje(
                    l[0].toString(), (String) l[1], justificativa(l), (String) l[5],
                    (String) l[6], (String) l[7],
                    l[2] == null ? null : ((Number) l[2]).doubleValue(),
                    toOdt(l[3]), ((Number) l[4]).intValue()));
        }
        return hoje;
    }

    private static java.time.OffsetDateTime toOdt(Object v) {
        if (v instanceof java.time.OffsetDateTime odt) {
            return odt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (v instanceof java.time.Instant inst) {
            return inst.atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    private static String justificativa(Object[] l) {
        if (l[2] != null) {
            return "dinheiro parado";
        }
        if (l[3] != null) {
            return "prazo próximo";
        }
        if (((Number) l[4]).intValue() > 0) {
            return "cobranças acumuladas";
        }
        return "aguardando há mais tempo";
    }

    // ---- helpers -----------------------------------------------------------

    private void exigirEntrada(OrgId org, UUID pendenciaId) {
        String status;
        try {
            status = (String) em.createNativeQuery("SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        } catch (NoResultException e) {
            throw new FalhasTriagem.EstadoInvalido("pendência inexistente");
        }
        if (!"ENTRADA".equals(status)) {
            throw new FalhasTriagem.EstadoInvalido("saída indisponível no estado " + status);
        }
    }

    private void setStatus(OrgId org, UUID pendenciaId, String status) {
        // fechada_em é setado ao fechar (007: expira o token de portal junto)
        em.createNativeQuery("""
                UPDATE pendencia
                SET status = ?, fechada_em = CASE WHEN ? = 'FECHADA' THEN now() ELSE fechada_em END
                WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, status).setParameter(2, status)
                .setParameter(3, org.valor()).setParameter(4, pendenciaId)
                .executeUpdate();
    }

    private int proximaOcorrencia(OrgId org, UUID pendenciaId) {
        Number n = (Number) em.createNativeQuery("SELECT ocorrencia FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        return n.intValue() + 1;
    }

    private void agendarDespertar(OrgId org, UUID pendenciaId, int ocorrencia, OffsetDateTime quando) {
        agenda.agendar(new TarefaAgendada(org, JOB_DESPERTAR,
                pendenciaId + ":" + ocorrencia, quando,
                "{\"pendencia_id\":\"" + pendenciaId + "\",\"ocorrencia\":" + ocorrencia + "}"));
    }
}
