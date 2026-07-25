package app.alcada.plataforma.gateway.internal;

import java.math.BigDecimal;
import java.util.UUID;

import app.alcada.plataforma.gateway.port.Destino;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Observabilidade por chamada (ADR-0020). Registra metadados — nunca prompt nem
 * resposta. A tabela {@code chamada_modelo} sequer tem colunas para isso.
 */
@ApplicationScoped
public class RegistroChamadas {

    private static final String INSERT = """
            INSERT INTO chamada_modelo
                (org_id, tarefa, sensibilidade, destino, provedor_efetivo, modelo,
                 tokens_in, tokens_out, latencia_ms, custo, schema_ok, ref_mensagem_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final EntityManager em;

    public RegistroChamadas(EntityManager em) {
        this.em = em;
    }

    public void registrar(OrgId org, String tarefa, Sensibilidade sensibilidade, Destino destino,
                          String provedor, String modelo, int tokensIn, int tokensOut,
                          int latenciaMs, BigDecimal custo, Boolean schemaOk, UUID refMensagemId) {
        em.createNativeQuery(INSERT)
                .setParameter(1, org.valor())
                .setParameter(2, tarefa)
                .setParameter(3, sensibilidade.name())
                .setParameter(4, destino.name())
                .setParameter(5, provedor)
                .setParameter(6, modelo)
                .setParameter(7, tokensIn)
                .setParameter(8, tokensOut)
                .setParameter(9, latenciaMs)
                .setParameter(10, custo == null ? BigDecimal.ZERO : custo)
                .setParameter(11, schemaOk)
                .setParameter(12, refMensagemId)
                .executeUpdate();
    }
}
