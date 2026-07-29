package app.alcada.captura.internal;

/**
 * Compromisso/demanda de decisão extraído de uma janela de conversa de grupo (024).
 * O modelo PROPÕE (INV-10); o código decide se vira pendência (só se
 * {@code dependeDoGestor} e confiança suficiente). Campos já re-hidratados (1º nome).
 */
public record Compromisso(
        boolean dependeDoGestor,
        String tipo,               // REUNIAO | APROVACAO | DECISAO | FOLLOW_UP | OUTRO
        String assunto,
        String quemPede,           // 1º nome/papel (identidade mínima — ADR-0011 emenda)
        String quandoTexto,        // "próxima segunda 14h" (como dito), ou null
        String quandoResolvido,    // ISO-8601 resolvido no fuso do tenant, ou null
        String acaoPendente,       // o que falta o gestor fazer/decidir, ou null
        boolean possivelmenteFeito,// sinais de conclusão no fio (ex.: "enviei!")
        double confianca) {
}
