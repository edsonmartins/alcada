package app.alcada.autonomia.internal;

import java.time.*;
import java.util.*;
import app.alcada.autonomia.port.CalendarioComercial;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class CalendarioComercialJdbc implements CalendarioComercial {
    private final EntityManager em;
    public CalendarioComercialJdbc(EntityManager em){this.em=em;}

    public record Configuracao(String timezone,Set<Integer> diasUteis,LocalTime inicio,LocalTime fim,
                               Map<LocalDate,String> feriados){}

    public Configuracao configuracao(OrgId org){
        @SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("""
                SELECT COALESCE(c.timezone,o.timezone),COALESCE(c.dias_uteis,'1,2,3,4,5'),
                       COALESCE(c.inicio,time '09:00'),COALESCE(c.fim,time '18:00')
                FROM organizacao o LEFT JOIN calendario_comercial c ON c.org_id=o.id WHERE o.id=?
                """).setParameter(1,org.valor()).getResultList();
        if(rs.isEmpty()) throw new IllegalArgumentException("organização inexistente");
        Object[] r=rs.getFirst();Set<Integer> dias=new LinkedHashSet<>();
        for(String s:r[1].toString().split(","))dias.add(Integer.parseInt(s));
        Map<LocalDate,String> feriados=new LinkedHashMap<>();
        @SuppressWarnings("unchecked") List<Object[]> fs=em.createNativeQuery("SELECT data,nome FROM feriado_comercial WHERE org_id=? ORDER BY data")
                .setParameter(1,org.valor()).getResultList();
        for(Object[] f:fs)feriados.put(data(f[0]),(String)f[1]);
        return new Configuracao((String)r[0],Set.copyOf(dias),hora(r[2]),hora(r[3]),Map.copyOf(feriados));
    }

    public void salvar(OrgId org,String timezone,Set<Integer> dias,LocalTime inicio,LocalTime fim,
                       Map<LocalDate,String> feriados){
        ZoneId.of(timezone);
        if(dias==null||dias.isEmpty()||dias.stream().anyMatch(d->d<1||d>7))throw new IllegalArgumentException("dias úteis inválidos");
        if(inicio==null||fim==null||!inicio.isBefore(fim))throw new IllegalArgumentException("horário comercial inválido");
        String csv=dias.stream().sorted().map(String::valueOf).reduce((a,b)->a+","+b).orElseThrow();
        em.createNativeQuery("""
                INSERT INTO calendario_comercial(org_id,timezone,dias_uteis,inicio,fim) VALUES (?,?,?,?,?)
                ON CONFLICT(org_id) DO UPDATE SET timezone=EXCLUDED.timezone,dias_uteis=EXCLUDED.dias_uteis,
                    inicio=EXCLUDED.inicio,fim=EXCLUDED.fim,atualizado_em=now()
                """).setParameter(1,org.valor()).setParameter(2,timezone).setParameter(3,csv)
                .setParameter(4,inicio).setParameter(5,fim).executeUpdate();
        em.createNativeQuery("DELETE FROM feriado_comercial WHERE org_id=?").setParameter(1,org.valor()).executeUpdate();
        if(feriados!=null)for(var f:feriados.entrySet())em.createNativeQuery("INSERT INTO feriado_comercial(org_id,data,nome) VALUES (?,?,?)")
                .setParameter(1,org.valor()).setParameter(2,f.getKey()).setParameter(3,f.getValue()).executeUpdate();
    }

    @Override public Duration tempoUtilEntre(OrgId org,OffsetDateTime inicio,OffsetDateTime fim){
        if(!fim.isAfter(inicio))return Duration.ZERO;Configuracao c=configuracao(org);ZoneId z=ZoneId.of(c.timezone());
        Instant de=inicio.toInstant(),ate=fim.toInstant();long segundos=0;
        LocalDate d=de.atZone(z).toLocalDate(),ultima=ate.atZone(z).toLocalDate();
        while(!d.isAfter(ultima)){if(util(c,d)){Instant a=d.atTime(c.inicio()).atZone(z).toInstant(),b=d.atTime(c.fim()).atZone(z).toInstant();
            Instant x=a.isAfter(de)?a:de,y=b.isBefore(ate)?b:ate;if(y.isAfter(x))segundos+=Duration.between(x,y).toSeconds();}d=d.plusDays(1);}
        return Duration.ofSeconds(segundos);
    }

    @Override public OffsetDateTime adicionarTempoUtil(OrgId org,OffsetDateTime inicio,Duration duracao){
        if(duracao.isNegative())throw new IllegalArgumentException("duração negativa");Configuracao c=configuracao(org);ZoneId z=ZoneId.of(c.timezone());
        Instant cursor=proximaAbertura(org,inicio).toInstant();long restante=duracao.toSeconds();
        if(restante==0)return cursor.atZone(z).toOffsetDateTime();
        for(int guarda=0;guarda<3700;guarda++){ZonedDateTime local=cursor.atZone(z);LocalDate d=local.toLocalDate();
            if(!util(c,d)){cursor=aberturaSeguinte(c,d.plusDays(1),z);continue;}
            Instant fecha=d.atTime(c.fim()).atZone(z).toInstant();long disp=Math.max(0,Duration.between(cursor,fecha).toSeconds());
            if(restante<=disp)return cursor.plusSeconds(restante).atZone(z).toOffsetDateTime();
            restante-=disp;cursor=aberturaSeguinte(c,d.plusDays(1),z);}
        throw new IllegalStateException("calendário não encontrou abertura");
    }

    @Override public OffsetDateTime proximaAbertura(OrgId org,OffsetDateTime instante){
        Configuracao c=configuracao(org);ZoneId z=ZoneId.of(c.timezone());Instant i=instante.toInstant();ZonedDateTime l=i.atZone(z);LocalDate d=l.toLocalDate();
        if(util(c,d)){Instant a=d.atTime(c.inicio()).atZone(z).toInstant(),b=d.atTime(c.fim()).atZone(z).toInstant();
            if(i.isBefore(a))return a.atZone(z).toOffsetDateTime();if(i.isBefore(b))return i.atZone(z).toOffsetDateTime();d=d.plusDays(1);}
        else d=d.plusDays(1);
        return aberturaSeguinte(c,d,z).atZone(z).toOffsetDateTime();
    }
    private static Instant aberturaSeguinte(Configuracao c,LocalDate d,ZoneId z){while(!util(c,d))d=d.plusDays(1);return d.atTime(c.inicio()).atZone(z).toInstant();}
    private static boolean util(Configuracao c,LocalDate d){return c.diasUteis().contains(d.getDayOfWeek().getValue())&&!c.feriados().containsKey(d);}
    private static LocalDate data(Object v){return v instanceof LocalDate d?d:((java.sql.Date)v).toLocalDate();}
    private static LocalTime hora(Object v){return v instanceof LocalTime h?h:((java.sql.Time)v).toLocalTime();}
}
