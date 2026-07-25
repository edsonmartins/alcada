package app.alcada.identidade.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Raiz do tenant. Não é dado escopado — é o próprio escopo (isenta do guarda org_id).
 */
@Entity
@Table(name = "organizacao")
public class Organizacao {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String nome;

    @Column(nullable = false)
    public String sku = "CLOUD";

    @Column(name = "criada_em", nullable = false)
    public OffsetDateTime criadaEm = OffsetDateTime.now();
}
