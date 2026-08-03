package app.alcada.autonomia.internal;

import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Persistência dos contatos externos de repasse (RFC-0008). */
@ApplicationScoped
public class ContatosExternosJdbc implements ContatosExternos {

    private static final java.util.Set<String> CANAIS = java.util.Set.of("WHATSAPP", "EMAIL");

    private final EntityManager em;

    public ContatosExternosJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId) {
        if (nome == null || nome.isBlank() || endereco == null || endereco.isBlank()) {
            throw new IllegalArgumentException("nome e endereço são obrigatórios");
        }
        if (!CANAIS.contains(canal)) {
            throw new IllegalArgumentException("canal inválido: " + canal);
        }
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
}
