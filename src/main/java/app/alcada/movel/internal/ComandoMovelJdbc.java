package app.alcada.movel.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.port.Autonomia;
import app.alcada.captura.port.EscapeCaptura;
import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.identidade.port.Pessoas;
import app.alcada.movel.port.Comando;
import app.alcada.movel.port.ComandoMovel;
import app.alcada.movel.port.ResultadoComando;
import app.alcada.movel.port.ResultadoComando.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.triagem.port.Triagem;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Sincronização de comandos do canal móvel (021). Cada comando roda na própria
 * transação (efeito + registro de idempotência juntos); o mesmo {@code comandoId}
 * nunca executa duas vezes (INV-13). Mapeia intenção → ação determinística das
 * portas existentes (INV-10). Escopo por organização (INV-15).
 */
@ApplicationScoped
public class ComandoMovelJdbc implements ComandoMovel {

    private final EntityManager em;
    private final Triagem triagem;
    private final Autonomia autonomia;
    private final EscapeCaptura escape;
    private final Consulta consulta;
    private final Pessoas pessoas;

    public ComandoMovelJdbc(EntityManager em, Triagem triagem, Autonomia autonomia,
                            EscapeCaptura escape, Consulta consulta, Pessoas pessoas) {
        this.em = em;
        this.triagem = triagem;
        this.autonomia = autonomia;
        this.escape = escape;
        this.consulta = consulta;
        this.pessoas = pessoas;
    }

    @Override
    public List<ResultadoComando> sincronizar(OrgId org, UUID pessoa, List<Comando> comandos) {
        List<ResultadoComando> out = new ArrayList<>(comandos.size());
        for (Comando c : comandos) {
            out.add(QuarkusTransaction.requiringNew().call(() -> processar(org, pessoa, c)));
        }
        return out;
    }

    private ResultadoComando processar(OrgId org, UUID pessoa, Comando c) {
        String[] gravado = buscar(org, c.comandoId());
        if (gravado != null) {
            // Reenvio (INV-13): devolve o resultado gravado, não re-executa. CONSULTAR
            // é leitura pura — repopula a resposta.
            ResultadoConsulta rc = c.intencao() == Comando.Intencao.CONSULTAR
                    ? consultarSeguro(org, c) : null;
            UUID pend = gravado[1] == null ? null : UUID.fromString(gravado[1]);
            return new ResultadoComando(c.comandoId(), Status.valueOf(gravado[0]), gravado[2], pend, rc);
        }
        ResultadoComando r = executar(org, pessoa, c);
        gravar(org, c, r);
        return r;
    }

    private ResultadoComando executar(OrgId org, UUID pessoa, Comando c) {
        Comando.Campos f = c.campos() == null ? vazio() : c.campos();
        try {
            return switch (c.intencao()) {
                case REGISTRAR -> ResultadoComando.ok(c.comandoId(),
                        escape.registrar(org, f.titulo(), f.quemEspera(), f.oQueTrava(), f.classe(), pessoa));
                case CONSULTAR -> new ResultadoComando(c.comandoId(), Status.OK, null, null,
                        consulta.consultar(org, f.pergunta()));
                default -> despachar(org, pessoa, c, f);
            };
        } catch (IllegalArgumentException e) {
            // ex.: REGISTRAR sem título / classe inválida
            return ResultadoComando.erro(c.comandoId(), e.getMessage());
        }
    }

    /** Saídas/repasse sobre uma pendência: se ela já saiu da fila, IGNORADO (C4). */
    private ResultadoComando despachar(OrgId org, UUID pessoa, Comando c, Comando.Campos f) {
        if (c.pendenciaId() == null || !triagem.emEntrada(org, c.pendenciaId())) {
            return ResultadoComando.ignorado(c.comandoId(), "pendência não está mais na entrada");
        }
        try {
            switch (c.intencao()) {
                case RESOLVER -> triagem.resolver(org, c.pendenciaId(), f.nota(), pessoa);
                case RESERVAR -> triagem.reservar(org, c.pendenciaId(), prazoOu(f.prazo(), 1), pessoa);
                case REPOUSAR -> triagem.repousar(org, c.pendenciaId(), prazoOu(f.voltaEm(), 7), pessoa);
                case ADIAR -> triagem.adiar(org, c.pendenciaId(), prazoOu(f.voltaEm(), 7), f.oQueFalta(), pessoa);
                case REPASSAR -> {
                    if (f.dono() == null || f.nivel() == null) {
                        return ResultadoComando.erro(c.comandoId(), "REPASSAR exige dono e nível");
                    }
                    autonomia.delegar(org, c.pendenciaId(), f.dono(), f.nivel(), prazoOu(f.prazo(), 2), pessoa);
                    // Memória durável (022): o termo falado vira apelido do dono (no-op se
                    // já casava pelo nome). Aprender é do gestor que despacha.
                    if (f.aliasFalado() != null) {
                        pessoas.aprender(org, pessoa, f.aliasFalado(), f.dono());
                    }
                }
                default -> { /* REGISTRAR/CONSULTAR tratados em executar */ }
            }
            return ResultadoComando.ok(c.comandoId(), c.pendenciaId());
        } catch (RuntimeException e) {
            return ResultadoComando.erro(c.comandoId(), e.getMessage());
        }
    }

    private ResultadoConsulta consultarSeguro(OrgId org, Comando c) {
        String p = c.campos() == null ? null : c.campos().pergunta();
        return p == null ? null : consulta.consultar(org, p);
    }

    // ---- persistência de idempotência --------------------------------------

    /** Devolve [status, pendencia_id, detalhe] gravados, ou null se ainda não visto. */
    private String[] buscar(OrgId org, UUID comandoId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery("""
                    SELECT status, pendencia_id, detalhe FROM comando_movel
                    WHERE org_id = ? AND comando_id = ?
                    """).setParameter(1, org.valor()).setParameter(2, comandoId).getSingleResult();
            return new String[] {
                    (String) r[0], r[1] == null ? null : r[1].toString(), (String) r[2]};
        } catch (NoResultException e) {
            return null;
        }
    }

    private void gravar(OrgId org, Comando c, ResultadoComando r) {
        em.createNativeQuery("""
                INSERT INTO comando_movel (org_id, comando_id, intencao, status, detalhe, pendencia_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, org.valor()).setParameter(2, c.comandoId())
                .setParameter(3, c.intencao().name()).setParameter(4, r.status().name())
                .setParameter(5, r.detalhe()).setParameter(6, r.pendenciaId())
                .executeUpdate();
    }

    private static OffsetDateTime prazoOu(String iso, int diasPadrao) {
        if (iso != null && !iso.isBlank()) {
            return OffsetDateTime.parse(iso);
        }
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(diasPadrao);
    }

    private static Comando.Campos vazio() {
        return new Comando.Campos(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
