package app.alcada.metricas.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Evidências agregadas do piloto; nunca decide G2/G7 automaticamente (028). */
@ApplicationScoped
public class PilotoJdbc {
    private final EntityManager em;

    public PilotoJdbc(EntityManager em) { this.em = em; }

    public Relatorio relatorio(OrgId org, OffsetDateTime inicio, OffsetDateTime fim) {
        Map<String, Long> eventos = contarEventos(org, inicio, fim);
        Object[] captura = (Object[]) em.createNativeQuery("""
                SELECT count(*) FILTER (WHERE coalesce(carga->>'escape','false') <> 'true'),
                       count(*) FILTER (WHERE carga->>'escape' = 'true')
                FROM trilha WHERE org_id = ? AND tipo = 'CAPTADA'
                  AND ocorrido_em >= ? AND ocorrido_em < ?
                """).setParameter(1, org.valor()).setParameter(2, inicio).setParameter(3, fim)
                .getSingleResult();
        long capturados = ((Number) captura[0]).longValue();
        long escapes = ((Number) captura[1]).longValue();
        Object[] aval = (Object[]) em.createNativeQuery("""
                SELECT count(*), count(*) FILTER (WHERE resultado='ERA_PENDENCIA'),
                       count(*) FILTER (WHERE resultado='INCONCLUSIVO')
                FROM avaliacao_descarte_piloto WHERE org_id = ? AND avaliado_em >= ? AND avaliado_em < ?
                """).setParameter(1, org.valor()).setParameter(2, inicio).setParameter(3, fim).getSingleResult();
        long fora = ((Number) em.createNativeQuery("""
                SELECT coalesce(sum(decisoes_fora_da_fila),0) FROM reconciliacao_piloto
                WHERE org_id = ? AND registrado_em >= ? AND registrado_em < ?
                """).setParameter(1, org.valor()).setParameter(2, inicio).setParameter(3, fim)
                .getSingleResult()).longValue();
        long fechados = eventos.getOrDefault("RESOLVIDA", 0L) + eventos.getOrDefault("EXECUTADA", 0L)
                + eventos.getOrDefault("EXECUTADA_POR_AUSENCIA", 0L) + eventos.getOrDefault("DECIDIDA_NO_BLOCO", 0L);
        long autonomos = eventos.getOrDefault("EXECUTADA_POR_AUSENCIA", 0L);
        return new Relatorio(inicio, fim,
                new N2(eventos.getOrDefault("PROPOSTA_REGISTRADA", 0L), autonomos,
                        eventos.getOrDefault("INTERROMPIDA", 0L),
                        eventos.getOrDefault("DEVOLVIDA_PELO_EXECUTOR", 0L),
                        eventos.getOrDefault("ESCALADA", 0L),
                        eventos.getOrDefault("DESFEITA_NA_JANELA", 0L)),
                new Captura(capturados, escapes, capturados + escapes == 0 ? 0
                        : Math.round(escapes * 1000.0 / (capturados + escapes)) / 10.0,
                        ((Number) aval[0]).longValue(), ((Number) aval[1]).longValue(),
                        ((Number) aval[2]).longValue(), fora,
                        "A taxa de escape é piso de misses conhecidos; a amostra não é recall exato."),
                new Autonomia(fechados, autonomos, fechados == 0 ? 0
                        : Math.round(autonomos * 1000.0 / fechados) / 10.0),
                fontes(org));
    }

    @Transactional
    public UUID reconciliar(OrgId org, LocalDate semana, int fora, String observacao, UUID ator) {
        if (semana == null || fora < 0) throw new IllegalArgumentException("semana e contagem válida são obrigatórias");
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO reconciliacao_piloto
                    (id,org_id,semana,decisoes_fora_da_fila,observacao,registrado_por)
                VALUES (?,?,?,?,?,?)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, semana)
                .setParameter(4, fora).setParameter(5, limitar(observacao)).setParameter(6, ator).executeUpdate();
        return id;
    }

    @SuppressWarnings("unchecked")
    public List<Descarte> amostra(OrgId org, OffsetDateTime inicio, OffsetDateTime fim, int limite, String semente) {
        List<Object[]> rs = em.createNativeQuery("""
                SELECT d.id, d.motivo, d.ocorrido_em, f.identificador, e.texto
                FROM descarte_captura d
                JOIN fonte f ON f.org_id=d.org_id AND f.id=d.fonte_id
                JOIN evento_bruto e ON e.org_id=d.org_id AND e.id=d.evento_bruto_id AND e.expira_em > now()
                WHERE d.org_id=? AND d.ocorrido_em>=? AND d.ocorrido_em<?
                ORDER BY md5(d.id::text || ?)
                LIMIT ?
                """).setParameter(1, org.valor()).setParameter(2, inicio).setParameter(3, fim)
                .setParameter(4, semente == null ? "" : semente).setParameter(5, Math.min(100, Math.max(1, limite)))
                .getResultList();
        return rs.stream().map(r -> new Descarte(r[0].toString(), (String) r[1], r[2].toString(),
                (String) r[3], limitar((String) r[4]))).toList();
    }

    @Transactional
    public UUID avaliar(OrgId org, UUID descarte, String resultado, UUID ator) {
        if (!List.of("ERA_PENDENCIA","NAO_ERA","INCONCLUSIVO").contains(resultado))
            throw new IllegalArgumentException("resultado inválido");
        Number existe = (Number) em.createNativeQuery("SELECT count(*) FROM descarte_captura WHERE org_id=? AND id=?")
                .setParameter(1, org.valor()).setParameter(2, descarte).getSingleResult();
        if (existe.longValue() == 0) throw new IllegalArgumentException("descarte não encontrado");
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO avaliacao_descarte_piloto (id,org_id,descarte_id,resultado,avaliado_por)
                VALUES (?,?,?,?,?)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, descarte)
                .setParameter(4, resultado).setParameter(5, ator).executeUpdate();
        return id;
    }

    private Map<String, Long> contarEventos(OrgId org, OffsetDateTime inicio, OffsetDateTime fim) {
        @SuppressWarnings("unchecked") List<Object[]> rs = em.createNativeQuery("""
                SELECT tipo,count(*) FROM trilha WHERE org_id=? AND ocorrido_em>=? AND ocorrido_em<? GROUP BY tipo
                """).setParameter(1, org.valor()).setParameter(2, inicio).setParameter(3, fim).getResultList();
        Map<String,Long> m = new LinkedHashMap<>();
        rs.forEach(r -> m.put((String) r[0], ((Number) r[1]).longValue()));
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Fonte> fontes(OrgId org) {
        List<Object[]> rs = em.createNativeQuery("""
                SELECT f.id,f.identificador,f.ativa,max(e.recebido_em),
                       coalesce(cp.janelas_vistas,0),coalesce(cp.janelas_processadas,0)
                FROM fonte f LEFT JOIN evento_bruto e ON e.org_id=f.org_id AND e.fonte_id=f.id
                LEFT JOIN captura_proporcao cp ON cp.org_id=f.org_id AND cp.fonte_id=f.id
                WHERE f.org_id=? GROUP BY f.id,f.identificador,f.ativa,cp.janelas_vistas,cp.janelas_processadas
                ORDER BY f.identificador
                """).setParameter(1, org.valor()).getResultList();
        return rs.stream().map(r -> new Fonte(r[0].toString(), (String) r[1], (Boolean) r[2],
                r[3] == null ? null : r[3].toString(), ((Number)r[4]).longValue(), ((Number)r[5]).longValue(),
                r[3] == null ? "TESTAR_FONTE" : "OK")).toList();
    }

    private static String limitar(String s) { return s == null ? null : s.substring(0, Math.min(500, s.length())); }

    public record Relatorio(OffsetDateTime inicio, OffsetDateTime fim, N2 n2, Captura captura,
                            Autonomia autonomia, List<Fonte> fontes) {}
    public record N2(long propostas,long porAusencia,long intervencoes,long devolucoes,long escaladas,long reversoes) {}
    public record Captura(long capturados,long escapes,double escapePct,long amostra,long falsosNegativos,
                          long inconclusivos,long decisoesForaDaFila,String aviso) {}
    public record Autonomia(long fechados,long autonomos,double fracaoPct) {}
    public record Fonte(String id,String nome,boolean ativa,String ultimoEvento,long vistas,long processadas,String acao) {}
    public record Descarte(String id,String motivo,String ocorridoEm,String fonte,String trecho) {}
}
