package app.alcada.autonomia.internal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

/** Persistência dos contatos externos de repasse (RFC-0008). */
@ApplicationScoped
public class ContatosExternosJdbc implements ContatosExternos {

    private final EntityManager em;

    public ContatosExternosJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId) {
        validar(nome, canal, endereco);
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO contato_externo (id, org_id, nome, canal, endereco, criado_por)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, id).setParameter(2, org.valor()).setParameter(3, nome)
                .setParameter(4, canal).setParameter(5, endereco).setParameter(6, gestorId)
                .executeUpdate();
        return id;
    }

    @Override
    @Transactional
    public boolean atualizar(OrgId org, UUID contatoId, String nome, String canal, String endereco) {
        validar(nome, canal, endereco);
        return em.createNativeQuery("""
                UPDATE contato_externo SET nome = ?, canal = ?, endereco = ?
                WHERE org_id = ? AND id = ?
                """)
                .setParameter(1, nome).setParameter(2, canal).setParameter(3, endereco)
                .setParameter(4, org.valor()).setParameter(5, contatoId)
                .executeUpdate() > 0;
    }

    private static void validar(String nome, String canal, String endereco) {
        if (nome == null || nome.isBlank() || endereco == null || endereco.isBlank()) {
            throw new IllegalArgumentException("nome e endereço são obrigatórios");
        }
        if (!CANAIS.contains(canal)) {
            throw new IllegalArgumentException("canal inválido: " + canal);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ContatoExterno> listar(OrgId org) {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT id, nome, canal, endereco FROM contato_externo WHERE org_id = ? ORDER BY nome")
                .setParameter(1, org.valor()).getResultList();
        return linhas.stream()
                .map(r -> new ContatoExterno((UUID) r[0], (String) r[1], (String) r[2], (String) r[3]))
                .toList();
    }

    @Override
    public List<ContatoExterno> buscarPorNome(OrgId org, String termo) {
        String[] alvo = tokens(termo);
        if (alvo.length == 0) {
            return List.of();
        }
        // Diretório pequeno (contato é escape, não cadastro): casa em memória com a
        // mesma regra do diretório de pessoas — prefixo de palavra, sem acento.
        return listar(org).stream().filter(c -> casa(c.nome(), alvo)).toList();
    }

    @Override
    public Optional<ContatoExterno> buscar(OrgId org, UUID contatoId) {
        if (contatoId == null) {
            return Optional.empty();
        }
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT id, nome, canal, endereco FROM contato_externo WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, contatoId).getSingleResult();
            return Optional.of(
                    new ContatoExterno((UUID) r[0], (String) r[1], (String) r[2], (String) r[3]));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    // ---- casamento por nome (espelha PessoasJdbc) ---------------------------

    /** Casa quando cada palavra do termo é prefixo de alguma palavra do nome. */
    private static boolean casa(String nome, String[] alvo) {
        for (String t : alvo) {
            boolean achou = false;
            for (String w : tokens(nome)) {
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
        return Arrays.stream(normalizar(s).split("\\s+")).filter(w -> !w.isBlank()).toArray(String[]::new);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }
}
