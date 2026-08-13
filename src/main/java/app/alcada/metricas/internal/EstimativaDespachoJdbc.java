package app.alcada.metricas.internal;

import java.util.OptionalInt;
import java.util.UUID;
import app.alcada.metricas.port.EstimativaDespacho;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class EstimativaDespachoJdbc implements EstimativaDespacho {
    private final EntityManager em;
    public EstimativaDespachoJdbc(EntityManager em){this.em=em;}

    @Override public OptionalInt minutos(OrgId org,UUID gestor,int quantidade){
        if(quantidade<=0)return OptionalInt.empty();
        Object[] r=(Object[])em.createNativeQuery("""
                WITH eventos AS (
                  SELECT ocorrido_em,lag(ocorrido_em) OVER (ORDER BY ocorrido_em) anterior
                  FROM trilha WHERE org_id=? AND ator=? AND ocorrido_em>=now()-interval '90 days'
                    AND tipo IN ('RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','INTERROMPIDA')
                ), intervalos AS (
                  SELECT extract(epoch FROM (ocorrido_em-anterior))/60.0 minutos FROM eventos
                  WHERE anterior IS NOT NULL AND ocorrido_em-anterior<=interval '15 minutes'
                ) SELECT count(*),percentile_cont(0.5) WITHIN GROUP (ORDER BY minutos) FROM intervalos
                """).setParameter(1,org.valor()).setParameter(2,"HUMANO:"+gestor).getSingleResult();
        if(((Number)r[0]).longValue()<5||r[1]==null)return OptionalInt.empty();
        double bruto=((Number)r[1]).doubleValue()*quantidade;
        return OptionalInt.of(Math.max(5,(int)(Math.round(bruto/5.0)*5)));
    }
}
