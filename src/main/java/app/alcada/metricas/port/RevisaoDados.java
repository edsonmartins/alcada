package app.alcada.metricas.port;

import java.util.List;

/**
 * Roteiro conduzido da revisão de sexta (20 min, MANUAL §rotina): a fila a
 * esvaziar, os adiados a decidir, a dica do que pode virar regra e o resumo da
 * semana. Leitura pura.
 */
public record RevisaoDados(
        Entrada entrada,
        List<RadarDados.ItemAdiado> adiados,
        List<DicaRegra> podeVirarRegra,
        ResumoSemana resumoSemana) {

    public record Entrada(long qtd, List<ItemFila> itens) {
    }

    public record ItemFila(String id, String titulo, String quemEspera) {
    }

    /** DICA, não regra: assinatura {classe} com repetição — aponta para a mineração (RFC-0003). */
    public record DicaRegra(String classe, long ocorrencias) {
    }

    public record ResumoSemana(long resolvidas, long executadas, long delegadas,
                               long escaladas, long devolvidas, long fechadas) {
    }
}
