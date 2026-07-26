package app.alcada.metricas.port;

import java.util.List;

/**
 * Diagnóstico organizacional (ADR-0017) — nunca placar pessoal. Contagens
 * honestas e separadas (ADR-0024/0025). Série de encolhimento é o número-âncora
 * do INV-01.
 */
public record RadarDados(
        Dependencia dependeDoGestor,
        long rodandoSemVoce,
        List<ItemAdiado> adiados,
        PiorEspera piorEspera,
        Autonomia autonomia,
        FechamentoCanal fechamentoCanal,
        List<SemanaFluxo> encolhimento,
        SaudeGateway saudeGateway) {

    /** Quanto ainda trava no gestor: ENTRADA + AGENDADA + DELEGADA(N3) sobre abertos. */
    public record Dependencia(long qtd, long total, int pct) {
    }

    /** Adiado 3×+ é diagnóstico, não priorização — a UI oferece ação (ADR-0017). */
    public record ItemAdiado(String id, String titulo, int adiadoCount,
                             String oQueTrava, String quemEspera, Double valorEmJogo) {
    }

    public record PiorEspera(String pendenciaId, String titulo, long dias, String quemEspera) {
    }

    /** Desfechos do N2 contados SEPARADAMENTE (ADR-0024). */
    public record Autonomia(long deliberada, long porAusencia, long devolvida,
                            long escalada, long promovida) {
    }

    /** Fechamento no canal: entregue × falho × impossível (ADR-0025). */
    public record FechamentoCanal(long entregue, long falho, long impossivel) {
    }

    /**
     * Saúde do gateway de modelos nos últimos 28 dias (018/009): chamadas, falhas
     * (schema recusado/indisponibilidade) e custo acumulado. Alerta = falhas &gt; 0.
     */
    public record SaudeGateway(long chamadas, long falhas, double custo) {
    }

    /** Fluxo semanal: entraram (CAPTADA) × fecharam. Proxy honesto do encolhimento. */
    public record SemanaFluxo(String semana, long entraram, long fecharam) {
    }
}
