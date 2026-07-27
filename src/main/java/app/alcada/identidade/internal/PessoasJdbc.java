package app.alcada.identidade.internal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import app.alcada.identidade.port.Pessoas;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Diretório de pessoas sobre a tabela {@code pessoa} + memória de apelidos em
 * {@code apelido_pessoa} (INV-15: filtro de org em toda query, sob o GuardaOrgId).
 * O casamento por nome é feito em memória (a org do piloto tem poucas pessoas):
 * normaliza (minúsculas, sem acento) e casa quando cada palavra do termo é
 * prefixo de alguma palavra do nome — "alexandre" casa "Alexandre Silva". O
 * próprio gestor nunca é candidato ao repasse.
 */
@ApplicationScoped
public class PessoasJdbc implements Pessoas {

    private final EntityManager em;

    public PessoasJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<PessoaRef> buscarPorNome(OrgId org, UUID gestorId, String termo) {
        String norm = normalizar(termo == null ? "" : termo);
        if (norm.isBlank()) {
            return List.of();
        }
        // 1) apelido aprendido pelo gestor tem prioridade.
        UUID porApelido = apelido(org, gestorId, norm);
        if (porApelido != null && !porApelido.equals(gestorId)) {
            Pessoa p = carregar(org, porApelido);
            if (p != null) {
                return List.of(new PessoaRef(p.id, p.nome));
            }
        }
        // 2) casamento por nome (excluindo o próprio gestor).
        String[] alvo = tokens(norm);
        return pessoasDaOrg(org, gestorId).stream()
                .filter(p -> casa(p.nome, alvo))
                .map(p -> new PessoaRef(p.id, p.nome))
                .toList();
    }

    @Override
    public List<PessoaRef> listar(OrgId org, UUID gestorId) {
        return pessoasDaOrg(org, gestorId).stream()
                .map(p -> new PessoaRef(p.id, p.nome))
                .toList();
    }

    @Override
    public void aprender(OrgId org, UUID gestorId, String termo, UUID pessoaId) {
        String norm = normalizar(termo == null ? "" : termo);
        if (norm.isBlank() || pessoaId == null || pessoaId.equals(gestorId)) {
            return;
        }
        Pessoa p = carregar(org, pessoaId);
        if (p == null || casa(p.nome, tokens(norm))) {
            return; // pessoa inexistente ou termo já resolve pelo nome → nada a aprender
        }
        em.createNativeQuery("""
                INSERT INTO apelido_pessoa (org_id, gestor_id, termo, pessoa_id, atualizado_em)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (org_id, gestor_id, termo)
                DO UPDATE SET pessoa_id = EXCLUDED.pessoa_id, atualizado_em = now()
                """)
                .setParameter(1, org.valor())
                .setParameter(2, gestorId)
                .setParameter(3, norm)
                .setParameter(4, pessoaId)
                .executeUpdate();
    }

    // ---- dados -------------------------------------------------------------

    private UUID apelido(OrgId org, UUID gestorId, String termoNorm) {
        try {
            Object id = em.createNativeQuery("""
                    SELECT pessoa_id FROM apelido_pessoa
                    WHERE org_id = ? AND gestor_id = ? AND termo = ?
                    """)
                    .setParameter(1, org.valor())
                    .setParameter(2, gestorId)
                    .setParameter(3, termoNorm)
                    .getSingleResult();
            return id == null ? null : UUID.fromString(id.toString());
        } catch (NoResultException e) {
            return null;
        }
    }

    private Pessoa carregar(OrgId org, UUID pessoaId) {
        return em.createQuery("select p from Pessoa p where p.orgId = :org and p.id = :id", Pessoa.class)
                .setParameter("org", org.valor())
                .setParameter("id", pessoaId)
                .getResultStream().findFirst().orElse(null);
    }

    private List<Pessoa> pessoasDaOrg(OrgId org, UUID gestorId) {
        return em.createQuery(
                        "select p from Pessoa p where p.orgId = :org and p.id <> :gestor", Pessoa.class)
                .setParameter("org", org.valor())
                .setParameter("gestor", gestorId)
                .getResultStream()
                .sorted(Comparator.comparing(p -> normalizar(p.nome)))
                .toList();
    }

    // ---- casamento ---------------------------------------------------------

    private static boolean casa(String nome, String[] alvo) {
        if (alvo.length == 0) {
            return false;
        }
        String[] palavras = tokens(nome);
        for (String t : alvo) {
            boolean achou = false;
            for (String w : palavras) {
                if (w.startsWith(t)) {
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                return false;
            }
        }
        return true;
    }

    private static String[] tokens(String s) {
        if (s == null || s.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(normalizar(s).split("\\s+"))
                .filter(w -> !w.isBlank())
                .toArray(String[]::new);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }
}
