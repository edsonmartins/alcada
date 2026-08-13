package app.alcada.autonomia;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import app.alcada.autonomia.internal.CalendarioComercialJdbc;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CalendarioComercialTest {
    @Inject EntityManager em;
    @Inject CalendarioComercialJdbc calendario;

    @Test void c1_admin_configura_calendario(){OrgId org=novaOrg("America/Sao_Paulo");String body=corpo("America/Sao_Paulo","2026-09-07");
        given().header("X-Org-Id",org.valor()).header("X-Papel","ADMIN").contentType("application/json").body(body)
                .put("/v1/calendario-comercial").then().statusCode(200);
        var c=calendario.configuracao(org);assertEquals(LocalTime.of(8,30),c.inicio());assertTrue(c.feriados().containsKey(LocalDate.of(2026,9,7)));}

    @Test void c2_nao_admin_nao_altera(){OrgId org=novaOrg("UTC");given().header("X-Org-Id",org.valor()).header("X-Papel","GESTOR")
            .contentType("application/json").body(corpo("UTC","2026-09-07")).put("/v1/calendario-comercial").then().statusCode(403);
        assertEquals(0L,contar(org,"SELECT count(*) FROM calendario_comercial WHERE org_id=?"));}

    @Test void c3_fim_de_semana_nao_conta(){OrgId org=config("America/Sao_Paulo",Map.of());
        var de=OffsetDateTime.parse("2026-08-14T16:00:00-03:00");var ate=OffsetDateTime.parse("2026-08-17T10:00:00-03:00");
        assertEquals(Duration.ofHours(2),calendario.tempoUtilEntre(org,de,ate));
        assertEquals(ate,calendario.adicionarTempoUtil(org,de,Duration.ofHours(2)));}

    @Test void c4_feriado_nao_conta(){OrgId org=config("America/Sao_Paulo",Map.of(LocalDate.of(2026,8,17),"Feriado"));
        var r=calendario.adicionarTempoUtil(org,OffsetDateTime.parse("2026-08-14T16:00:00-03:00"),Duration.ofHours(2));
        assertEquals(OffsetDateTime.parse("2026-08-18T10:00:00-03:00"),r);}

    @Test void c5_fuso_e_offset_preservados(){OrgId org=config("America/New_York",Map.of());
        var r=calendario.adicionarTempoUtil(org,OffsetDateTime.parse("2027-03-12T16:00:00-05:00"),Duration.ofHours(2));
        assertEquals(OffsetDateTime.parse("2027-03-15T10:00:00-04:00"),r);}

    @Test void c11_tenant_nao_cruza_calendario(){OrgId a=config("UTC",Map.of(LocalDate.of(2026,8,17),"Fecha"));OrgId b=config("UTC",Map.of());
        OffsetDateTime de=OffsetDateTime.parse("2026-08-17T09:00:00Z");
        OffsetDateTime ate=OffsetDateTime.parse("2026-08-17T17:00:00Z");
        assertEquals(Duration.ZERO,calendario.tempoUtilEntre(a,de,ate));assertEquals(Duration.ofHours(8),calendario.tempoUtilEntre(b,de,ate));}

    @Test void c12_nova_pendencia_nao_publica_notificacao(){OrgId org=novaOrg("UTC");QuarkusTransaction.requiringNew().run(()->
        em.createNativeQuery("INSERT INTO pendencia(id,org_id,titulo,classe,horizonte,status) VALUES (?,?, 'Nova','DECISAO','HOJE','ENTRADA')")
                .setParameter(1,UUID.randomUUID()).setParameter(2,org.valor()).executeUpdate());
        assertEquals(0L,contar(org,"SELECT count(*) FROM outbox WHERE org_id=?"));}

    private OrgId config(String zona,Map<LocalDate,String> feriados){OrgId o=novaOrg(zona);QuarkusTransaction.requiringNew().run(()->calendario.salvar(o,zona,Set.of(1,2,3,4,5),LocalTime.of(9,0),LocalTime.of(17,0),feriados));return o;}
    private OrgId novaOrg(String zona){OrgId o=new OrgId(UUID.randomUUID());QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO organizacao(id,nome,timezone) VALUES (?,'Org',?)").setParameter(1,o.valor()).setParameter(2,zona).executeUpdate());return o;}
    private long contar(OrgId o,String sql){return ((Number)QuarkusTransaction.requiringNew().call(()->em.createNativeQuery(sql).setParameter(1,o.valor()).getSingleResult())).longValue();}
    private static String corpo(String z,String feriado){return "{\"timezone\":\""+z+"\",\"diasUteis\":[1,2,3,4,5],\"inicio\":\"08:30\",\"fim\":\"17:30\",\"feriados\":[{\"data\":\""+feriado+"\",\"nome\":\"Feriado\"}]}";}
}
