package app.alcada.regras.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import app.alcada.regras.port.Aprendizado;
import app.alcada.regras.port.Mineracao;
import app.alcada.regras.port.PerguntaAprendizado;
import app.alcada.regras.port.PropostaRegra;
import app.alcada.regras.port.Regras;
import app.alcada.regras.port.Resposta;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Laço de aprendizado (RFC-0003, ADR-0019). Determinístico: as candidatas vêm da
 * mineração (010). O "sim" cria a regra (confirmação humana, INV-10). Disciplina:
 * 1 pergunta aberta por classe (índice único), teto de 3/semana, recusa não
 * re-pergunta na semana. Escopado por org_id (INV-15).
 */
@ApplicationScoped
public class AprendizadoJdbc implements Aprendizado {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final int TETO_SEMANA = 3;
    private static final Map<String, Integer> RANK = Map.of("N3", 1, "N2", 2, "N1", 3);

    private final EntityManager em;
    private final Mineracao mineracao;
    private final Regras regras;
    private final Trilha trilha;

    public AprendizadoJdbc(EntityManager em, Mineracao mineracao, Regras regras, Trilha trilha) {
        this.em = em;
        this.mineracao = mineracao;
        this.regras = regras;
        this.trilha = trilha;
    }

    @Override
    public List<PerguntaAprendizado> perguntasAbertas(OrgId org) {
        UUID orgId = org.valor();
        Map<String, PropostaRegra> candidatas = mineracao.propostas(org).stream()
                .collect(Collectors.toMap(PropostaRegra::classe, p -> p, (a, b) -> a));

        gerar(org, candidatas);

        // Lista as abertas, enriquecidas com a evidência da candidata (se ainda houver).
        @SuppressWarnings("unchecked")
        List<Object[]> abertas = em.createNativeQuery(
                "SELECT id, classe FROM pergunta_aprendizado WHERE org_id = ? AND status = 'ABERTA' ORDER BY criada_em")
                .setParameter(1, orgId).getResultList();
        List<PerguntaAprendizado> res = new ArrayList<>(abertas.size());
        for (Object[] l : abertas) {
            String classe = (String) l[1];
            PropostaRegra p = candidatas.get(classe);
            res.add(new PerguntaAprendizado(l[0].toString(), classe,
                    p == null ? "N1" : p.nivelSugerido(),
                    p == null ? null : p.donoSugerido(),
                    p == null ? 0 : p.ocorrencias(),
                    p == null ? List.of() : p.casos()));
        }
        return res;
    }

    private void gerar(OrgId org, Map<String, PropostaRegra> candidatas) {
        UUID orgId = org.valor();
        OffsetDateTime inicioSemana = LocalDate.now(SP)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(SP).toOffsetDateTime();

        Set<String> abertas = classes(orgId, "status = 'ABERTA'", null);
        Set<String> recusadasSemana = classes(orgId, "status = 'RECUSADA' AND respondida_em >= ?", inicioSemana);
        long criadasSemana = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM pergunta_aprendizado WHERE org_id = ? AND criada_em >= ?")
                .setParameter(1, orgId).setParameter(2, inicioSemana).getSingleResult()).longValue();
        long restante = TETO_SEMANA - criadasSemana;

        for (PropostaRegra p : candidatas.values()) {
            if (restante <= 0) {
                break;
            }
            if (abertas.contains(p.classe()) || recusadasSemana.contains(p.classe()) || p.casos().isEmpty()) {
                continue;
            }
            UUID ref = UUID.fromString(p.casos().get(0).pendenciaId());
            em.createNativeQuery("""
                    INSERT INTO pergunta_aprendizado (org_id, classe, pendencia_ref) VALUES (?, ?, ?)
                    """).setParameter(1, orgId).setParameter(2, p.classe()).setParameter(3, ref).executeUpdate();
            registrar(org, ref, TipoEvento.SUGESTAO_EMITIDA, null, p.classe());
            abertas.add(p.classe());
            restante--;
        }
    }

    @Override
    public void responder(OrgId org, UUID perguntaId, Resposta resposta, UUID por) {
        UUID orgId = org.valor();
        Object[] row;
        try {
            row = (Object[]) em.createNativeQuery(
                    "SELECT classe, pendencia_ref, status FROM pergunta_aprendizado WHERE org_id = ? AND id = ?")
                    .setParameter(1, orgId).setParameter(2, perguntaId).getSingleResult();
        } catch (NoResultException e) {
            throw new NoSuchElementException("pergunta não encontrada");
        }
        String classe = (String) row[0];
        UUID ref = (UUID) row[1];
        if (!"ABERTA".equals(row[2])) {
            throw new IllegalStateException("pergunta já respondida");
        }

        switch (resposta) {
            case SIM -> {
                PropostaRegra p = mineracao.propostas(org).stream()
                        .filter(x -> x.classe().equals(classe)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("padrão não é mais candidato"));
                // Dono da delegação automática: o sugerido (mais frequente) ou, se a classe
                // nunca foi delegada, quem respondeu "sim" (o gestor).
                String dono = p.donoSugerido() != null ? p.donoSugerido() : (por != null ? por.toString() : null);
                if (dono == null) {
                    throw new IllegalArgumentException("sem dono — defina a regra em /alcadas");
                }
                String max = regras.nivelMaximo(org, classe);
                if (max != null && RANK.get(p.nivelSugerido()) > RANK.getOrDefault(max, 3)) {
                    throw new IllegalArgumentException("nível sugerido excede o máximo da classe");
                }
                if (!regras.existeRegraAtiva(org, classe)) {
                    regras.criar(org, classe, p.nivelSugerido(), UUID.fromString(dono));
                }
                fechar(orgId, perguntaId, "ACEITA", por);
                registrar(org, ref, TipoEvento.SUGESTAO_ACEITA, por, classe);
            }
            case AGORA_NAO -> {
                fechar(orgId, perguntaId, "RECUSADA", por);
                registrar(org, ref, TipoEvento.SUGESTAO_RECUSADA, por, classe);
            }
            case NAO_PERGUNTAR -> {
                fechar(orgId, perguntaId, "SILENCIADA", por);
                regras.silenciar(org, classe, por);
                registrar(org, ref, TipoEvento.SUGESTAO_SILENCIADA, por, classe);
            }
        }
    }

    private Set<String> classes(UUID orgId, String cond, OffsetDateTime param) {
        var q = em.createNativeQuery(
                "SELECT DISTINCT classe FROM pergunta_aprendizado WHERE org_id = ? AND " + cond)
                .setParameter(1, orgId);
        if (param != null) {
            q.setParameter(2, param);
        }
        @SuppressWarnings("unchecked")
        List<String> l = q.getResultList();
        return new HashSet<>(l);
    }

    private void fechar(UUID orgId, UUID perguntaId, String status, UUID por) {
        em.createNativeQuery("""
                UPDATE pergunta_aprendizado SET status = ?, respondida_em = now(), respondida_por = ?
                WHERE org_id = ? AND id = ?
                """).setParameter(1, status).setParameter(2, por)
                .setParameter(3, orgId).setParameter(4, perguntaId).executeUpdate();
    }

    private void registrar(OrgId org, UUID pendencia, TipoEvento tipo, UUID por, String classe) {
        Ator ator = por != null ? Ator.humano(por) : Ator.sistemaMotor("aprendizado");
        trilha.registrar(new EventoTrilha(org, pendencia, tipo, ator, null, null, null,
                "{\"classe\":\"" + classe + "\"}"));
    }
}
