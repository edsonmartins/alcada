package app.alcada.identidade.internal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import app.alcada.identidade.port.Pessoas;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Diretório de pessoas sobre a tabela {@code pessoa} (INV-15: filtro de org na
 * query, sob o GuardaOrgId). O casamento por nome é feito em memória (a org do
 * piloto tem poucas pessoas): normaliza (minúsculas, sem acento) e casa quando
 * cada palavra do termo é prefixo de alguma palavra do nome — assim "alexandre"
 * casa "Alexandre Silva" e "executor" casa "Executor Piloto".
 */
@ApplicationScoped
public class PessoasJdbc implements Pessoas {

    private final EntityManager em;

    public PessoasJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<PessoaRef> buscarPorNome(OrgId org, String termo) {
        String[] alvo = tokens(termo);
        if (alvo.length == 0) {
            return List.of();
        }
        return em.createQuery("select p from Pessoa p where p.orgId = :org", Pessoa.class)
                .setParameter("org", org.valor())
                .getResultStream()
                .filter(p -> casa(p.nome, alvo))
                .sorted(Comparator.comparing(p -> normalizar(p.nome)))
                .map(p -> new PessoaRef(p.id, p.nome))
                .toList();
    }

    private static boolean casa(String nome, String[] alvo) {
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
