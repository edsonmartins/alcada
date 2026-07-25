package app.alcada.plataforma.gateway.internal;

import java.util.List;

/**
 * Política fixa aplicada pelo gateway em toda chamada externa (ADR-0020 §2).
 * Não é parametrizável pelo chamador. Só a lista {@code only} vem de
 * configuração; os demais campos são constantes.
 */
public record PoliticaProvedor(
        List<String> only,
        boolean allowFallbacks,
        String dataCollection,
        boolean zdr,
        boolean requireParameters) {

    public static PoliticaProvedor fixa(List<String> only) {
        return new PoliticaProvedor(List.copyOf(only), false, "deny", true, true);
    }
}
