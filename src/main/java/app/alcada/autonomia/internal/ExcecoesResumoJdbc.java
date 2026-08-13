package app.alcada.autonomia.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.port.DecisoesRetorno;
import app.alcada.autonomia.port.ExcecoesResumo;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Read model de autonomia para P035 e decisão explícita dos retornos observados. */
@ApplicationScoped
public class ExcecoesResumoJdbc implements ExcecoesResumo, DecisoesRetorno {
    private final EntityManager em;
    private final Trilha trilha;

    public ExcecoesResumoJdbc(EntityManager em, Trilha trilha) {
        this.em = em;
        this.trilha = trilha;
    }

    @Override
    public Conteudo listar(OrgId org, UUID gestorId, OffsetDateTime executarAte) {
        List<Item> n2 = consultar("""
                SELECT p.id,d.id,p.titulo,j.executar_em,NULL
                FROM delegacao d JOIN pendencia p ON p.org_id=d.org_id AND p.id=d.pendencia_id
                JOIN job j ON j.org_id=d.org_id AND j.tipo='AUT_VIRADA' AND j.chave=d.id::text
                WHERE d.org_id=? AND d.gestor_id=? AND d.nivel='N2' AND d.status='AGUARDANDO_JANELA'
                  AND j.status='AGENDADO' AND j.executar_em<=?
                ORDER BY j.executar_em,p.id LIMIT 3
                """, org, gestorId, executarAte);
        long totalN2 = contar("""
                SELECT count(*) FROM delegacao d JOIN job j ON j.org_id=d.org_id
                  AND j.tipo='AUT_VIRADA' AND j.chave=d.id::text
                WHERE d.org_id=? AND d.gestor_id=? AND d.nivel='N2' AND d.status='AGUARDANDO_JANELA'
                  AND j.status='AGENDADO' AND j.executar_em<=?
                """, org, gestorId, executarAte);

        List<Item> retornos = consultar("""
                SELECT p.id,r.id,p.titulo,r.recebido_em,r.trecho_minimizado
                FROM retorno_delegacao r
                LEFT JOIN delegacao d ON d.org_id=r.org_id AND d.id=r.delegacao_id
                LEFT JOIN pedido_informacao pi ON pi.org_id=r.org_id AND pi.id=r.pedido_informacao_id
                JOIN pendencia p ON p.org_id=r.org_id AND p.id=COALESCE(d.pendencia_id,pi.pendencia_id)
                WHERE r.org_id=? AND COALESCE(d.gestor_id,pi.gestor_id)=? AND r.estado='OBSERVADO'
                  AND p.status<>'FECHADA'
                ORDER BY r.recebido_em,p.id LIMIT 3
                """, org, gestorId, null);
        long totalRetornos = contarSemData("""
                SELECT count(*) FROM retorno_delegacao r
                LEFT JOIN delegacao d ON d.org_id=r.org_id AND d.id=r.delegacao_id
                LEFT JOIN pedido_informacao pi ON pi.org_id=r.org_id AND pi.id=r.pedido_informacao_id
                JOIN pendencia p ON p.org_id=r.org_id AND p.id=COALESCE(d.pendencia_id,pi.pendencia_id)
                WHERE r.org_id=? AND COALESCE(d.gestor_id,pi.gestor_id)=? AND r.estado='OBSERVADO'
                  AND p.status<>'FECHADA'
                """, org, gestorId);

        List<Item> escalados = consultar("""
                SELECT p.id,d.id,p.titulo,t.ocorrido_em,NULL
                FROM delegacao d JOIN pendencia p ON p.org_id=d.org_id AND p.id=d.pendencia_id
                JOIN LATERAL (SELECT ocorrido_em FROM trilha WHERE org_id=d.org_id
                    AND pendencia_id=d.pendencia_id AND tipo='ESCALADA' ORDER BY ocorrido_em DESC LIMIT 1) t ON true
                WHERE d.org_id=? AND d.gestor_id=? AND d.status='ESCALADA' AND p.status='ENTRADA'
                  AND NOT EXISTS (SELECT 1 FROM trilha x WHERE x.org_id=d.org_id
                    AND x.pendencia_id=d.pendencia_id AND x.ocorrido_em>t.ocorrido_em
                    AND x.ator LIKE 'HUMANO:%' AND x.tipo IN ('RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','INTERROMPIDA'))
                ORDER BY t.ocorrido_em,p.id LIMIT 3
                """, org, gestorId, null);
        long totalEscalados = contarSemData("""
                SELECT count(*) FROM delegacao d JOIN pendencia p ON p.org_id=d.org_id AND p.id=d.pendencia_id
                JOIN LATERAL (SELECT ocorrido_em FROM trilha WHERE org_id=d.org_id
                    AND pendencia_id=d.pendencia_id AND tipo='ESCALADA' ORDER BY ocorrido_em DESC LIMIT 1) t ON true
                WHERE d.org_id=? AND d.gestor_id=? AND d.status='ESCALADA' AND p.status='ENTRADA'
                  AND NOT EXISTS (SELECT 1 FROM trilha x WHERE x.org_id=d.org_id
                    AND x.pendencia_id=d.pendencia_id AND x.ocorrido_em>t.ocorrido_em
                    AND x.ator LIKE 'HUMANO:%' AND x.tipo IN ('RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','INTERROMPIDA'))
                """, org, gestorId);
        return new Conteudo(n2,totalN2,retornos,totalRetornos,escalados,totalEscalados);
    }

    @Override @Transactional
    public void decidir(OrgId org, UUID gestorId, UUID retornoId, String decisao, String chave) {
        if (chave == null || chave.isBlank()) throw new IllegalArgumentException("Idempotency-Key é obrigatório");
        String destino = switch (decisao == null ? "" : decisao) {
            case "APLICAR" -> "APLICADO";
            case "REJEITAR" -> "REJEITADO";
            default -> throw new IllegalArgumentException("decisão deve ser APLICAR ou REJEITAR");
        };
        @SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("""
                SELECT r.estado,r.decisao_chave,COALESCE(d.gestor_id,pi.gestor_id),
                       COALESCE(d.pendencia_id,pi.pendencia_id)
                FROM retorno_delegacao r
                LEFT JOIN delegacao d ON d.org_id=r.org_id AND d.id=r.delegacao_id
                LEFT JOIN pedido_informacao pi ON pi.org_id=r.org_id AND pi.id=r.pedido_informacao_id
                WHERE r.org_id=? AND r.id=? FOR UPDATE OF r
                """).setParameter(1,org.valor()).setParameter(2,retornoId).getResultList();
        if(rs.isEmpty()) throw new NaoEncontrado();
        Object[] r=rs.getFirst();
        if(!gestorId.equals(r[2])) throw new Proibido();
        if(!"OBSERVADO".equals(r[0])) {
            if(destino.equals(r[0]) && chave.equals(r[1])) return;
            throw new Conflito("retorno já possui decisão diferente");
        }
        @SuppressWarnings("unchecked") List<Object> donaChave=em.createNativeQuery("""
                SELECT id FROM retorno_delegacao WHERE org_id=? AND decisao_chave=? AND id<>?
                """).setParameter(1,org.valor()).setParameter(2,chave).setParameter(3,retornoId).getResultList();
        if(!donaChave.isEmpty()) throw new Conflito("Idempotency-Key já utilizada");
        try {
            em.createNativeQuery("""
                    UPDATE retorno_delegacao SET estado=?,decisao_chave=?,avaliado_por=?,avaliado_em=now()
                    WHERE org_id=? AND id=? AND estado='OBSERVADO'
                    """).setParameter(1,destino).setParameter(2,chave).setParameter(3,gestorId)
                    .setParameter(4,org.valor()).setParameter(5,retornoId).executeUpdate();
            trilha.registrar(new EventoTrilha(org,(UUID)r[3],TipoEvento.RETORNO_AVALIADO,
                    Ator.humano(gestorId),null,null,null,
                    "{\"retorno_id\":\""+retornoId+"\",\"decisao\":\""+decisao+"\"}"));
        } catch (RuntimeException e) {
            if (e.getClass().getName().contains("ConstraintViolation")
                    || (e.getCause()!=null&&e.getCause().getClass().getName().contains("ConstraintViolation")))
                throw new Conflito("Idempotency-Key já utilizada");
            throw e;
        }
    }

    private List<Item> consultar(String sql,OrgId org,UUID gestor,OffsetDateTime ate){
        var q=em.createNativeQuery(sql).setParameter(1,org.valor()).setParameter(2,gestor);
        if(ate!=null)q.setParameter(3,ate);
        @SuppressWarnings("unchecked") List<Object[]> rs=q.getResultList();
        List<Item> itens=new ArrayList<>(rs.size());
        for(Object[] r:rs)itens.add(new Item((UUID)r[0],(UUID)r[1],(String)r[2],tempo(r[3]),(String)r[4]));
        return itens;
    }
    private long contar(String sql,OrgId org,UUID gestor,OffsetDateTime ate){return ((Number)em.createNativeQuery(sql)
            .setParameter(1,org.valor()).setParameter(2,gestor).setParameter(3,ate).getSingleResult()).longValue();}
    private long contarSemData(String sql,OrgId org,UUID gestor){return ((Number)em.createNativeQuery(sql)
            .setParameter(1,org.valor()).setParameter(2,gestor).getSingleResult()).longValue();}
    private static OffsetDateTime tempo(Object v){if(v instanceof OffsetDateTime o)return o;if(v instanceof Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);if(v instanceof Instant i)return i.atOffset(ZoneOffset.UTC);return OffsetDateTime.parse(v.toString());}
}
