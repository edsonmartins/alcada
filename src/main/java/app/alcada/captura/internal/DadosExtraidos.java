package app.alcada.captura.internal;

import java.util.List;

/**
 * Saída estruturada da extração (RFC-0001), já re-hidratada. É proposta do
 * modelo (INV-10); a decisão de roteamento/alçada é de regra determinística.
 */
record DadosExtraidos(
        String titulo,
        String quemEspera,
        String oQueTrava,
        String prazoImplicito,     // ISO-8601 ou null
        java.math.BigDecimal valorEmJogo,
        List<String> entidades,
        String classeSugerida,     // DECISAO | BLOQUEIO | ESTEIRA
        Double confianca) {

    String textoComparacao() {
        return (titulo == null ? "" : titulo) + " " + (oQueTrava == null ? "" : oQueTrava);
    }
}
