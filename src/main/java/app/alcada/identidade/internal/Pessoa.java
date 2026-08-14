package app.alcada.identidade.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pessoa da organização. Dado escopado por tenant (INV-15): toda consulta
 * precisa filtrar {@code org_id}, sob pena do {@code GuardaOrgId}.
 */
@Entity
@Table(name = "pessoa")
public class Pessoa {

    @Id
    public UUID id;

    @Column(name = "org_id", nullable = false)
    public UUID orgId;

    @Column(nullable = false)
    public String nome;

    @Column
    public String email;

    @Column
    public String whatsapp;

    @Column(name = "criada_em", nullable = false)
    public OffsetDateTime criadaEm = OffsetDateTime.now();
}
