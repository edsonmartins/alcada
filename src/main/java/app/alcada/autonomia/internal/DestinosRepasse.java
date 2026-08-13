package app.alcada.autonomia.internal;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.identidade.port.Pessoas;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/** Consulta unificada e não vigilante dos destinos de repasse (029). */
@ApplicationScoped
public class DestinosRepasse {

    private final Pessoas pessoas;
    private final ContatosExternos contatos;
    private final EntityManager em;

    public DestinosRepasse(Pessoas pessoas, ContatosExternos contatos, EntityManager em) {
        this.pessoas = pessoas;
        this.contatos = contatos;
        this.em = em;
    }

    public List<Destino> buscar(OrgId org, UUID gestorId, String termo, String classe, int limite) {
        String busca = termo == null ? "" : termo.trim();
        List<Destino> candidatos = new ArrayList<>();
        var internas = busca.isBlank() ? pessoas.listar(org, gestorId) : pessoas.buscarPorNome(org, gestorId, busca);
        var externas = busca.isBlank() ? contatos.listar(org) : contatos.buscarPorNome(org, busca);

        internas.forEach(p -> candidatos.add(montar(org, gestorId, "INTERNO", p.id(), p.nome(),
                "Equipe", null, classe)));
        externas.forEach(c -> candidatos.add(montar(org, gestorId, "EXTERNO", c.id(), c.nome(),
                c.canal() + " · " + mascarar(c.canal(), c.endereco()), c.canal(), classe)));

        String norm = normalizar(busca);
        return candidatos.stream()
                .sorted(Comparator
                        .comparing((Destino d) -> !normalizar(d.nome()).equals(norm))
                        .thenComparing((Destino d) -> !d.usadoNaClasse())
                        .thenComparing((Destino d) -> !d.recente())
                        .thenComparing(d -> normalizar(d.nome())))
                .limit(Math.max(1, Math.min(limite, 8)))
                .toList();
    }

    private Destino montar(OrgId org, UUID gestorId, String tipo, UUID id, String nome,
                           String detalhe, String canal, String classe) {
        Historico h = historico(org, gestorId, tipo, id, classe);
        String prazo = h.duracaoSegundos() == null ? null
                : OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(h.duracaoSegundos()).toString();
        return new Destino(tipo, id.toString(), nome, detalhe, canal, h.recente(), h.usadoNaClasse(),
                h.nivel(), prazo);
    }

    private Historico historico(OrgId org, UUID gestorId, String tipo, UUID destino, String classe) {
        String coluna = "EXTERNO".equals(tipo) ? "d.contato_id" : "d.dono_id";
        @SuppressWarnings("unchecked")
        List<Object[]> rs = em.createNativeQuery(("""
                SELECT d.nivel, EXTRACT(EPOCH FROM (d.prazo - d.criada_em))::bigint,
                       d.criada_em > now() - interval '90 days', p.classe = ?
                FROM delegacao d
                JOIN pendencia p ON p.org_id = d.org_id AND p.id = d.pendencia_id
                JOIN trilha t ON t.org_id = d.org_id AND t.pendencia_id = d.pendencia_id
                              AND t.tipo = 'REPASSADA' AND t.ator = ?
                WHERE d.org_id = ? AND %s = ?
                ORDER BY d.criada_em DESC LIMIT 1
                """).formatted(coluna))
                .setParameter(1, classe == null ? "" : classe)
                .setParameter(2, "HUMANO:" + gestorId)
                .setParameter(3, org.valor())
                .setParameter(4, destino)
                .getResultList();
        if (rs.isEmpty()) return new Historico(false, false, null, null);
        Object[] r = rs.getFirst();
        return new Historico(Boolean.TRUE.equals(r[2]), Boolean.TRUE.equals(r[3]),
                Boolean.TRUE.equals(r[3]) ? (String) r[0] : null,
                Boolean.TRUE.equals(r[3]) && r[1] != null ? ((Number) r[1]).longValue() : null);
    }

    private static String mascarar(String canal, String endereco) {
        if (endereco == null || endereco.isBlank()) return "";
        if ("EMAIL".equals(canal)) {
            int at = endereco.indexOf('@');
            return at <= 1 ? "•••" : endereco.substring(0, 1) + "•••" + endereco.substring(at);
        }
        String digitos = endereco.replaceAll("\\D", "");
        return digitos.length() <= 4 ? "••••" : "•••• " + digitos.substring(digitos.length() - 4);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private record Historico(boolean recente, boolean usadoNaClasse, String nivel, Long duracaoSegundos) {}

    public record Destino(String tipo, String id, String nome, String detalhe, String canal,
                          boolean recente, boolean usadoNaClasse, String nivelSugerido,
                          String prazoSugerido) {}
}
