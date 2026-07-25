package app.alcada.captura.port;

import java.util.Map;

/**
 * Resultado de uma minimização (ADR-0020 §3, RFC-0007). Carrega o texto
 * minimizado e o mapa pseudônimo→real <b>efêmero</b>, vivo apenas nesta
 * instância, em memória, durante a chamada. Nunca é persistido — persistir
 * criaria o dado sensível que a minimização existe para evitar.
 *
 * <p>Cada minimização tem seu próprio mapa; por construção, o token de um item
 * não se re-hidrata no outro.
 */
public final class Minimizacao {

    private final String textoMinimizado;
    private final Map<String, String> pseudonimoParaReal;

    public Minimizacao(String textoMinimizado, Map<String, String> pseudonimoParaReal) {
        this.textoMinimizado = textoMinimizado;
        this.pseudonimoParaReal = Map.copyOf(pseudonimoParaReal);
    }

    public String textoMinimizado() {
        return textoMinimizado;
    }

    /** Reverte os pseudônimos DESTE item na resposta do modelo. */
    public String rehidratar(String resposta) {
        if (resposta == null) {
            return null;
        }
        String r = resposta;
        for (Map.Entry<String, String> e : pseudonimoParaReal.entrySet()) {
            r = r.replace(e.getKey(), e.getValue());
        }
        return r;
    }
}
