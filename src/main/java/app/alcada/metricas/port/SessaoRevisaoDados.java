package app.alcada.metricas.port;

import java.time.OffsetDateTime;
import java.util.List;
import app.alcada.regras.port.PropostaRegra;

/** Estado canônico da sessão resolutiva; não é uma cópia editável da fila. */
public record SessaoRevisaoDados(String id,String status,OffsetDateTime iniciadaEm,OffsetDateTime concluidaEm,
        RevisaoDados revisao,List<PropostaRegra> propostas,List<CandidataNivel> candidatasNivel,
        ImpactoTrimestre trimestre,ResumoSessao resumo){
    public record CandidataNivel(String classe,String donoId,String dono,String nivelAtual,String nivelSugerido,
                                 long ocorrencias,List<Fonte> fontes){}
    public record Fonte(String pendenciaId,String titulo,String href){}
    public record ImpactoTrimestre(long quantidade,Double valorEmJogo,List<Fonte> fontes,String acaoHref){}
    public record ResumoSessao(long triadas,long fechadas,long repassadas,long repousadas,long blocosAbertos,
        long regrasAceitas,long regrasRecusadas,long regrasObservadas,long niveisPromovidos,long protecoesAgenda,
        long dependenciasRemovidas,long continuamDependendo,List<Fonte> remanescentes,boolean improdutiva){}
}
