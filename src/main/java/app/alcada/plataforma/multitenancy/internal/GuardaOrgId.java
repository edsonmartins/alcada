package app.alcada.plataforma.multitenancy.internal;

import java.util.Set;
import java.util.regex.Pattern;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * INV-15 — fail-closed: recusa qualquer SQL emitido pelo Hibernate que toque
 * uma tabela sob tenant sem carregar {@code org_id}.
 *
 * <p>Registrado como {@code hibernate.session_factory.statement_inspector} em
 * {@code application.properties}; o Hibernate o instancia uma única vez no boot
 * (por isso o {@link RegisterForReflection}, para o native image). É stateless.
 *
 * <p>Distinção deliberada:
 * <ul>
 *   <li><b>Dados de tenant</b> ({@code pessoa}, {@code trilha}): leitura/alteração
 *       exige {@code org_id} num <em>predicado</em> — nunca basta a coluna estar
 *       na projeção. INSERT exige a coluna presente (valor informado).</li>
 *   <li><b>Filas de infraestrutura</b> ({@code outbox}, {@code job}): os workers
 *       varrem cross-tenant por projeto; basta {@code org_id} aparecer (o SELECT
 *       de claim o traz, para propagar o tenant ao efeito).</li>
 * </ul>
 *
 * <p>Tabelas-raiz (ex.: {@code organizacao}) são isentas. Migrations do Flyway
 * não passam por aqui (JDBC próprio).
 */
@RegisterForReflection
public class GuardaOrgId implements StatementInspector {

    static final Set<String> DADOS_TENANT = Set.of(
            "pessoa", "trilha",
            "evento_bruto", "entidade", "regra_autonomia", "pendencia", "cobranca",
            "delegacao", "classe_decisao", "ausencia", "adiamento", "token_portal",
            "regra_silenciada");
    static final Set<String> FILAS_TENANT = Set.of(
            "outbox", "job", "tarefa_reprocesso", "chamada_modelo", "descarte_captura",
            // fonte: o webhook resolve o tenant lendo org_id da própria fonte (id+segredo),
            // então basta org_id aparecer; não há predicado de org antes de conhecê-lo.
            "fonte");

    /** org_id em posição de predicado: seguido de operador de comparação. */
    private static final Pattern PREDICADO_ORG_ID =
            Pattern.compile("\\borg_id\\b\\s*(=|!=|<>|<|>|in\\b|is\\b|between\\b)");
    private static final Pattern COLUNA_ORG_ID = Pattern.compile("\\borg_id\\b");

    @Override
    public String inspect(String sql) {
        if (sql == null) {
            return null;
        }
        String s = sql.toLowerCase();

        for (String tabela : DADOS_TENANT) {
            if (!referencia(s, tabela)) {
                continue;
            }
            boolean insert = Pattern.compile("insert\\s+into\\s+" + tabela + "\\b").matcher(s).find();
            if (insert) {
                if (!COLUNA_ORG_ID.matcher(s).find()) {
                    throw viola(tabela, "INSERT sem coluna org_id", sql);
                }
            } else if (!PREDICADO_ORG_ID.matcher(s).find()) {
                throw viola(tabela, "sem filtro de org_id no predicado", sql);
            }
        }

        for (String tabela : FILAS_TENANT) {
            if (referencia(s, tabela) && !COLUNA_ORG_ID.matcher(s).find()) {
                throw viola(tabela, "sem org_id", sql);
            }
        }
        return sql;
    }

    private static IllegalStateException viola(String tabela, String motivo, String sql) {
        return new IllegalStateException("INV-15: query sobre '" + tabela + "' " + motivo + " — " + sql);
    }

    /** Detecta a tabela em posição de from/join/into/update/delete. */
    private boolean referencia(String sql, String tabela) {
        return Pattern.compile("\\b(from|join|into|update|delete\\s+from)\\s+" + tabela + "\\b")
                .matcher(sql).find();
    }
}
