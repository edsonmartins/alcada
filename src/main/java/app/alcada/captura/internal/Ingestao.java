package app.alcada.captura.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import app.alcada.captura.port.MensagemRecebida;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Ingestão: autentica a fonte, grava o bruto (idempotente por mensagem_id) e
 * agenda o processamento — nada é processado no thread do webhook (RFC-0001).
 */
@ApplicationScoped
public class Ingestao {

    /** Retenção curta do bruto (ADR-0011): 30 dias. */
    static final int RETENCAO_DIAS = 30;

    private final EntityManager em;
    private final Agenda agenda;

    public Ingestao(EntityManager em, Agenda agenda) {
        this.em = em;
        this.agenda = agenda;
    }

    /** Fonte resolvida a partir de (id, segredo): dá o tenant e a atividade. */
    public record FonteResolvida(OrgId org, boolean ativa) {
    }

    /** Resolve e autentica a fonte. {@code null} se id/segredo não conferem. */
    public FonteResolvida autenticarFonte(String fonteId, String segredo) {
        try {
            Object[] linha = (Object[]) em.createNativeQuery(
                    "SELECT org_id, ativa FROM fonte WHERE id = ? AND segredo = ?")
                    .setParameter(1, UUID.fromString(fonteId))
                    .setParameter(2, segredo)
                    .getSingleResult();
            return new FonteResolvida(new OrgId((UUID) linha[0]), (Boolean) linha[1]);
        } catch (jakarta.persistence.NoResultException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Grava o bruto e agenda processamento. Idempotente: reentrega da mesma
     * {@code mensagemId} não cria segundo bruto nem segundo processamento.
     *
     * @return o id do evento bruto criado, ou {@code null} se já existia
     */
    @Transactional
    public UUID ingerir(OrgId org, MensagemRecebida m) {
        UUID id = UUID.randomUUID();
        OffsetDateTime expira = OffsetDateTime.now(ZoneOffset.UTC).plusDays(RETENCAO_DIAS);

        int inseridos = em.createNativeQuery("""
                INSERT INTO evento_bruto
                    (id, org_id, fonte_id, mensagem_id, autor_ext, texto, anexos_ref, thread_ref, expira_em, grupo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (fonte_id, mensagem_id) DO NOTHING
                """)
                .setParameter(1, id)
                .setParameter(2, org.valor())
                .setParameter(3, UUID.fromString(m.fonteId()))
                .setParameter(4, m.mensagemId())
                .setParameter(5, m.autorExt())
                .setParameter(6, m.texto())
                .setParameter(7, m.anexosRef() == null ? null : String.join(",", m.anexosRef()))
                .setParameter(8, m.threadRef())
                .setParameter(9, expira)
                .setParameter(10, m.grupo())
                .executeUpdate();

        if (inseridos == 0) {
            return null; // reentrega: já ingerido (ADR-0021 dedup por mensagem_id)
        }

        agenda.agendar(new TarefaAgendada(
                org, TiposJob.PROCESSAR_CAPTURA, id.toString(),
                OffsetDateTime.now(ZoneOffset.UTC),
                "{\"evento_bruto_id\":\"" + id + "\"}"));
        return id;
    }
}
