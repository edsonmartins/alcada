package app.alcada.plataforma.trilha.internal;

import java.util.UUID;
import java.util.regex.Pattern;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Escritor append-only da trilha (INV-11). INSERT explícito por SQL nativo —
 * sem mapear entidade com chave composta de partição, e sem qualquer caminho
 * de UPDATE/DELETE. O banco reforça a imutabilidade por trigger + REVOKE.
 *
 * <p>O INSERT carrega {@code org_id}, satisfazendo o {@code GuardaOrgId}.
 *
 * <p>Guarda de dados (ADR-0016): {@code origem} e {@code carga} referenciam por
 * id — nunca identificador direto. Um validador barra CPF/CNPJ, e-mail e
 * telefone, para que a eliminação LGPD opere só em {@code identidade} sem
 * precisar tocar a trilha imutável.
 */
@ApplicationScoped
public class TrilhaAppendOnly implements Trilha {

    private static final String INSERT = """
            INSERT INTO trilha
                (id, org_id, pendencia_id, tipo, ator, ocorrido_em,
                 origem, estado_anterior, estado_posterior, carga)
            VALUES
                (?, ?, ?, ?, ?, now(),
                 cast(? as jsonb), ?, ?, cast(? as jsonb))
            """;

    // Identificadores diretos que não podem ser persistidos na trilha
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CNPJ =
            Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    // Os limites também excluem hífen para não confundir um trecho só numérico
    // de UUID (ex.: 12345678-abcd-...) com telefone.
    private static final Pattern TELEFONE = Pattern.compile(
            "(?<![\\p{Alnum}-])(?:\\+?55\\s?)?\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}(?![\\p{Alnum}-])");

    private final EntityManager em;

    public TrilhaAppendOnly(EntityManager em) {
        this.em = em;
    }

    @Override
    public UUID registrar(EventoTrilha e) {
        rejeitarIdentificadorDireto(e.origemJson());
        rejeitarIdentificadorDireto(e.cargaJson());

        UUID id = UUID.randomUUID();
        em.createNativeQuery(INSERT)
                .setParameter(1, id)
                .setParameter(2, e.org().valor())
                .setParameter(3, e.pendenciaId())
                .setParameter(4, e.tipo().name())
                .setParameter(5, e.ator().valor())
                .setParameter(6, e.origemJson())
                .setParameter(7, e.estadoAnterior())
                .setParameter(8, e.estadoPosterior())
                .setParameter(9, e.cargaJson())
                .executeUpdate();
        return id;
    }

    @Override
    public UUID compensar(OrgId org, UUID pendenciaId, Ator ator, UUID eventoCompensadoId, String motivo) {
        String carga = "{\"evento_compensado_id\":\"" + eventoCompensadoId + "\",\"motivo\":"
                + jsonString(motivo) + "}";
        return registrar(new EventoTrilha(
                org, pendenciaId, TipoEvento.COMPENSACAO, ator,
                null, null, null, carga));
    }

    private void rejeitarIdentificadorDireto(String json) {
        if (json == null) {
            return;
        }
        if (CPF.matcher(json).find() || CNPJ.matcher(json).find()
                || EMAIL.matcher(json).find() || TELEFONE.matcher(json).find()) {
            throw new IllegalArgumentException(
                    "ADR-0016: identificador direto não pode ser persistido na trilha — referencie por id");
        }
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
