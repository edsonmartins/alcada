package app.alcada.autonomia.internal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

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
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

/**
 * Motor de autonomia (RFC-0002). LLM propõe, código executa (INV-10):
 * roteamento, execução e escalonamento são regras determinísticas. Efeito
 * externo só após a janela de reversibilidade (INV-14), sempre via outbox.
 */
@ApplicationScoped
public class MotorAutonomia {

    private static final Map<String, Integer> CONSERVADORISMO = Map.of("N1", 1, "N2", 2, "N3", 3);
    private static final Duration JANELA_PADRAO = Duration.ofHours(4);
    private static final Duration ESCALONAMENTO_PADRAO = Duration.ofHours(24);

    private final EntityManager em;
    private final Agenda agenda;
    private final Outbox outbox;
    private final Trilha trilha;

    public MotorAutonomia(EntityManager em, Agenda agenda, Outbox outbox, Trilha trilha) {
        this.em = em;
        this.agenda = agenda;
        this.outbox = outbox;
        this.trilha = trilha;
    }

    // ---- ações de comando --------------------------------------------------

    /** Delega uma pendência. Aplica elegibilidade e modo ausência. Devolve a delegação. */
    @Transactional
    public UUID delegar(OrgId org, UUID pendenciaId, UUID donoId, String nivelPedido,
                        OffsetDateTime prazo, UUID gestorId) {
        String classe = classePendencia(org, pendenciaId);
        Parametros p = parametros(org, classe);

        boolean convertido = gestorAusente(org, gestorId);
        String nivel = convertido ? "N3" : nivelPedido;

        if (CONSERVADORISMO.get(nivel) < CONSERVADORISMO.get(p.nivelMaximo())) {
            throw new Falhas.Inelegivel("classe " + classe + " não elegível ao nível " + nivel
                    + " (máximo " + p.nivelMaximo() + ")");
        }

        UUID delegacaoId = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO delegacao (id, org_id, pendencia_id, dono_id, nivel, prazo,
                                       janela, escalonamento, status)
                VALUES (?, ?, ?, ?, ?, ?, (?::bigint * interval '1 second'),
                        (?::bigint * interval '1 second'), 'ABERTA')
                """)
                .setParameter(1, delegacaoId).setParameter(2, org.valor()).setParameter(3, pendenciaId)
                .setParameter(4, donoId).setParameter(5, nivel).setParameter(6, prazo)
                .setParameter(7, p.janela().toSeconds()).setParameter(8, p.escalonamento().toSeconds())
                .executeUpdate();

        atualizarStatusPendencia(org, pendenciaId, "DELEGADA");

        // Repassar (4R) é ação humana; a conversão por ausência é do sistema.
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.REPASSADA,
                Ator.humano(gestorId), "ENTRADA", "DELEGADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"nivel\":\"" + nivel + "\"}"));
        if (convertido) {
            trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.CONVERTIDA_POR_AUSENCIA,
                    Ator.sistemaMotor("autonomia"), null, null, null,
                    "{\"de\":\"" + nivelPedido + "\",\"para\":\"N3\"}"));
            notificar(org, "delegacao.criada", delegacaoId, "convertida para N3 por ausência do gestor");
        }

        // Só N2 tem o ciclo de ausência (vencimento/janela/escalonamento).
        if ("N2".equals(nivel)) {
            agenda.agendar(new TarefaAgendada(org, TiposAutonomia.VENCIMENTO, delegacaoId.toString(),
                    prazo, payload(delegacaoId)));
            agenda.agendar(new TarefaAgendada(org, TiposAutonomia.ESCALONAMENTO, delegacaoId.toString(),
                    prazo.plus(p.escalonamento()), payload(delegacaoId)));
        }
        return delegacaoId;
    }

    /** Executor conclui deliberadamente: EXECUTADA imediata (sem janela — ADR-0024/005). */
    @Transactional
    public void concluir(OrgId org, UUID delegacaoId, String resultado, UUID executorId) {
        exigirDono(org, delegacaoId, executorId);
        Delegacao d = carregar(org, delegacaoId);
        if (!emAberto(d.status())) {
            throw new Falhas.EstadoInvalido("delegação não pode ser concluída no estado " + d.status());
        }
        setStatusDelegacao(org, delegacaoId, "EXECUTADA");
        atualizarStatusPendencia(org, d.pendenciaId(), "FECHADA");
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.EXECUTADA,
                Ator.humano(executorId), "DELEGADA", "FECHADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"resultado\":" + json(resultado) + "}"));
        // efeito via outbox (imediato = sem janela de espera, não chamada no request)
        outbox.publicar(new MensagemOutbox(org, "delegacao.executada",
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"pendencia_id\":\"" + d.pendenciaId() + "\"}",
                delegacaoId + ":concluida"));
        // INV-09: aviso ao solicitante — entrega no canal de origem é do pacote 006
        outbox.publicar(new MensagemOutbox(org, "item.fechado",
                "{\"pendencia_id\":\"" + d.pendenciaId() + "\"}", d.pendenciaId() + ":fechado"));
    }

    /** Executor recusa a delegação: devolve ao gestor (≠ ESCALADA — ADR-0024). */
    @Transactional
    public void devolver(OrgId org, UUID delegacaoId, String motivo, UUID executorId) {
        exigirDono(org, delegacaoId, executorId);
        Delegacao d = carregar(org, delegacaoId);
        if (!emAberto(d.status())) {
            throw new Falhas.EstadoInvalido("delegação não pode ser devolvida no estado " + d.status());
        }
        setStatusDelegacao(org, delegacaoId, "DEVOLVIDA");
        atualizarStatusPendencia(org, d.pendenciaId(), "ENTRADA");
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.DEVOLVIDA_PELO_EXECUTOR,
                Ator.humano(executorId), "DELEGADA", "ENTRADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"motivo\":" + json(motivo) + "}"));
        outbox.publicar(new MensagemOutbox(org, "delegacao.devolvida",
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"motivo\":" + json(motivo) + "}",
                delegacaoId + ":devolvida"));
    }

    /** Executor registra proposta: habilita a execução por ausência. */
    @Transactional
    public void propor(OrgId org, UUID delegacaoId, String proposta, UUID executorId) {
        exigirDono(org, delegacaoId, executorId);
        Delegacao d = carregar(org, delegacaoId);
        if (!"ABERTA".equals(d.status()) && !"PROPOSTA".equals(d.status())) {
            throw new Falhas.EstadoInvalido("delegação não aceita proposta no estado " + d.status());
        }
        em.createNativeQuery(
                "UPDATE delegacao SET proposta = ?, proposta_em = now(), status = 'PROPOSTA' WHERE org_id = ? AND id = ?")
                .setParameter(1, proposta).setParameter(2, org.valor()).setParameter(3, delegacaoId)
                .executeUpdate();
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.PROPOSTA_REGISTRADA,
                Ator.humano(executorId), null, null, null, "{\"delegacao_id\":\"" + delegacaoId + "\"}"));
    }

    /** Gestor intervém antes do prazo: devolve à entrada, sem efeito externo. */
    @Transactional
    public void intervir(OrgId org, UUID pendenciaId, UUID gestorId) {
        Delegacao d = carregarAtual(org, pendenciaId, "ABERTA", "PROPOSTA", "AGUARDANDO_JANELA");
        setStatusDelegacao(org, d.id(), "DEVOLVIDA");
        atualizarStatusPendencia(org, pendenciaId, "ENTRADA");
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.INTERROMPIDA,
                Ator.humano(gestorId), "DELEGADA", "ENTRADA", null, "{\"delegacao_id\":\"" + d.id() + "\"}"));
        notificar(org, "delegacao.interrompida", d.id(), "gestor interrompeu; nenhum efeito emitido");
    }

    /** Desfazer: só vale dentro da janela (INV-14). Após publicação → 409. */
    @Transactional
    public void desfazer(OrgId org, UUID pendenciaId, UUID gestorId) {
        Delegacao d = carregarUltima(org, pendenciaId);
        switch (d.status()) {
            case "AGUARDANDO_JANELA" -> {
                setStatusDelegacao(org, d.id(), "DEVOLVIDA");
                atualizarStatusPendencia(org, pendenciaId, "ENTRADA");
                trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.DESFEITA_NA_JANELA,
                        Ator.humano(gestorId), "DELEGADA", "ENTRADA", null,
                        "{\"delegacao_id\":\"" + d.id() + "\"}"));
            }
            case "EXECUTADA" -> throw new Falhas.JanelaExpirada(
                    "janela encerrada e efeito publicado; abra nova pendência de reversão");
            default -> throw new Falhas.EstadoInvalido("desfazer indisponível no estado " + d.status());
        }
    }

    // ---- handlers de job (chamados pelos ExecutorJob) ----------------------

    /** No vencimento: se há proposta, entra na janela; senão deixa para o escalonamento. */
    @Transactional
    public void aoVencimento(OrgId org, UUID delegacaoId) {
        Delegacao d = carregar(org, delegacaoId);
        if (!"PROPOSTA".equals(d.status())) {
            return; // sem proposta (silêncio de ambos) ou já resolvida
        }
        setStatusDelegacao(org, delegacaoId, "AGUARDANDO_JANELA");
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.JANELA_INICIADA,
                Ator.sistemaMotor("autonomia"), "DELEGADA", "DELEGADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"janela\":\"" + d.janela() + "\"}"));
        // agenda a virada ao fim da janela — só então o efeito externo pode sair
        agenda.agendar(new TarefaAgendada(org, TiposAutonomia.VIRADA, delegacaoId.toString(),
                OffsetDateTime.now(ZoneOffset.UTC).plus(d.janela()), payload(delegacaoId)));
    }

    /** No fim da janela: executa por ausência e publica o efeito externo. */
    @Transactional
    public void aVirada(OrgId org, UUID delegacaoId) {
        Delegacao d = carregar(org, delegacaoId);
        if (!"AGUARDANDO_JANELA".equals(d.status())) {
            return; // desfeita ou intervinda dentro da janela
        }
        setStatusDelegacao(org, delegacaoId, "EXECUTADA");
        atualizarStatusPendencia(org, d.pendenciaId(), "FECHADA");
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.EXECUTADA_POR_AUSENCIA,
                Ator.sistemaMotor("autonomia"), "DELEGADA", "FECHADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"proposta\":" + json(d.proposta())
                        + ",\"janela\":\"" + d.janela() + "\",\"intervencoes\":[]}"));
        outbox.publicar(new MensagemOutbox(org, "delegacao.executada_por_ausencia",
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"pendencia_id\":\"" + d.pendenciaId() + "\"}",
                delegacaoId + ":executada"));
    }

    /** No escalonamento: silêncio de ambos não executa em branco — escala. */
    @Transactional
    public void aoEscalonamento(OrgId org, UUID delegacaoId) {
        Delegacao d = carregar(org, delegacaoId);
        if (!"ABERTA".equals(d.status())) {
            return; // houve proposta ou já progrediu — escalonamento não se aplica
        }
        setStatusDelegacao(org, delegacaoId, "ESCALADA");
        atualizarStatusPendencia(org, d.pendenciaId(), "ENTRADA");
        trilha.registrar(new EventoTrilha(org, d.pendenciaId(), TipoEvento.ESCALADA,
                Ator.sistemaMotor("autonomia"), "DELEGADA", "ENTRADA", null,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"motivo\":\"silencio_de_ambos\"}"));
        notificar(org, "delegacao.escalada", delegacaoId, "ninguém agiu; subiu ao gestor");
    }

    // ---- helpers -----------------------------------------------------------

    private record Delegacao(UUID id, UUID pendenciaId, String status, Duration janela, String proposta) {
    }

    private record Parametros(String nivelMaximo, Duration janela, Duration escalonamento) {
    }

    private Delegacao carregar(OrgId org, UUID delegacaoId) {
        Object[] r = (Object[]) em.createNativeQuery("""
                SELECT pendencia_id, status, EXTRACT(EPOCH FROM janela)::bigint, proposta
                FROM delegacao WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, org.valor()).setParameter(2, delegacaoId).getSingleResult();
        return new Delegacao(delegacaoId, (UUID) r[0], (String) r[1],
                Duration.ofSeconds(((Number) r[2]).longValue()), (String) r[3]);
    }

    private Delegacao carregarAtual(OrgId org, UUID pendenciaId, String... statuses) {
        String inList = String.join("','", statuses);
        try {
            Object[] r = (Object[]) em.createNativeQuery("""
                    SELECT id, status, EXTRACT(EPOCH FROM janela)::bigint, proposta
                    FROM delegacao
                    WHERE org_id = ? AND pendencia_id = ? AND status IN ('""" + inList + "')"
                    + " ORDER BY criada_em DESC LIMIT 1")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
            return new Delegacao((UUID) r[0], pendenciaId, (String) r[1],
                    Duration.ofSeconds(((Number) r[2]).longValue()), (String) r[3]);
        } catch (NoResultException e) {
            throw new Falhas.EstadoInvalido("nenhuma delegação ativa para a pendência");
        }
    }

    private Delegacao carregarUltima(OrgId org, UUID pendenciaId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery("""
                    SELECT id, status, EXTRACT(EPOCH FROM janela)::bigint, proposta
                    FROM delegacao WHERE org_id = ? AND pendencia_id = ? ORDER BY criada_em DESC LIMIT 1
                    """)
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
            return new Delegacao((UUID) r[0], pendenciaId, (String) r[1],
                    Duration.ofSeconds(((Number) r[2]).longValue()), (String) r[3]);
        } catch (NoResultException e) {
            throw new Falhas.EstadoInvalido("nenhuma delegação para a pendência");
        }
    }

    private String classePendencia(OrgId org, UUID pendenciaId) {
        try {
            return (String) em.createNativeQuery("SELECT classe FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        } catch (NoResultException e) {
            throw new Falhas.EstadoInvalido("pendência inexistente");
        }
    }

    private Parametros parametros(OrgId org, String classe) {
        try {
            Object[] r = (Object[]) em.createNativeQuery("""
                    SELECT nivel_maximo, EXTRACT(EPOCH FROM janela_reversibilidade)::bigint,
                           EXTRACT(EPOCH FROM escalonamento)::bigint
                    FROM classe_decisao WHERE org_id = ? AND classe = ?
                    """)
                    .setParameter(1, org.valor()).setParameter(2, classe).getSingleResult();
            return new Parametros((String) r[0],
                    Duration.ofSeconds(((Number) r[1]).longValue()),
                    Duration.ofSeconds(((Number) r[2]).longValue()));
        } catch (NoResultException e) {
            return new Parametros("N1", JANELA_PADRAO, ESCALONAMENTO_PADRAO); // sem classe: sem cap
        }
    }

    /** Delegação ainda passível de ação do executor. */
    private static boolean emAberto(String status) {
        return "ABERTA".equals(status) || "PROPOSTA".equals(status) || "AGUARDANDO_JANELA".equals(status);
    }

    /** Fronteira de autorização (ADR-0013): só o dono age na delegação. */
    private void exigirDono(OrgId org, UUID delegacaoId, UUID pessoa) {
        Object dono;
        try {
            dono = em.createNativeQuery("SELECT dono_id FROM delegacao WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, delegacaoId).getSingleResult();
        } catch (NoResultException e) {
            throw new Falhas.EstadoInvalido("delegação inexistente");
        }
        if (!pessoa.equals(dono)) {
            throw new Falhas.NaoAutorizado("ação restrita ao dono da delegação");
        }
    }

    private boolean gestorAusente(OrgId org, UUID gestorId) {
        Number n = (Number) em.createNativeQuery("""
                SELECT count(*) FROM ausencia
                WHERE org_id = ? AND pessoa_id = ? AND de <= now() AND ate >= now()
                """)
                .setParameter(1, org.valor()).setParameter(2, gestorId).getSingleResult();
        return n.longValue() > 0;
    }

    private void atualizarStatusPendencia(OrgId org, UUID pendenciaId, String status) {
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

    private void setStatusDelegacao(OrgId org, UUID delegacaoId, String status) {
        em.createNativeQuery("UPDATE delegacao SET status = ? WHERE org_id = ? AND id = ?")
                .setParameter(1, status).setParameter(2, org.valor()).setParameter(3, delegacaoId)
                .executeUpdate();
    }

    private void notificar(OrgId org, String tipo, UUID delegacaoId, String nota) {
        outbox.publicar(new MensagemOutbox(org, tipo,
                "{\"delegacao_id\":\"" + delegacaoId + "\",\"nota\":" + json(nota) + "}",
                delegacaoId + ":" + tipo));
    }

    private static String payload(UUID delegacaoId) {
        return "{\"delegacao_id\":\"" + delegacaoId + "\"}";
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
