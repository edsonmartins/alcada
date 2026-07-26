package app.alcada.captura.internal;

import java.util.List;
import java.util.UUID;

import app.alcada.captura.port.EscapeCaptura;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Escape manual (ADR-0005): pendência direto em ENTRADA + trilha CAPTADA (ator
 * humano; sem identificador direto na carga — ADR-0016). Escopo por org (INV-15).
 */
@ApplicationScoped
public class EscapeCapturaJdbc implements EscapeCaptura {

    private static final List<String> CLASSES = List.of("DECISAO", "BLOQUEIO", "ESTEIRA");

    private final EntityManager em;
    private final Trilha trilha;

    public EscapeCapturaJdbc(EntityManager em, Trilha trilha) {
        this.em = em;
        this.trilha = trilha;
    }

    @Override
    @Transactional
    public UUID registrar(OrgId org, String titulo, String quemEspera, String oQueTrava, String classe, UUID pessoa) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("titulo é obrigatório");
        }
        String c = classe == null ? "DECISAO" : classe;
        if (!CLASSES.contains(c)) {
            throw new IllegalArgumentException("classe deve ser DECISAO, BLOQUEIO ou ESTEIRA");
        }
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, quem_espera, o_que_trava, classe, horizonte, status)
                VALUES (?, ?, ?, ?, ?, ?, 'SEMANA', 'ENTRADA')
                """)
                .setParameter(1, id).setParameter(2, org.valor())
                .setParameter(3, titulo).setParameter(4, quemEspera)
                .setParameter(5, oQueTrava).setParameter(6, c)
                .executeUpdate();
        trilha.registrar(new EventoTrilha(org, id, TipoEvento.CAPTADA,
                Ator.humano(pessoa), null, "ENTRADA", null, "{\"escape\":true}"));
        return id;
    }
}
