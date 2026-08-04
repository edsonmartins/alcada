package app.alcada.notificacao.port;

import app.alcada.notificacao.port.ContasCalendario.Conta;

/**
 * Troca o código do consentimento OAuth por tokens (RFC-0009). O gestor autoriza
 * no provedor; o app manda só o código, e é aqui que ele vira uma {@link Conta}.
 * A Alçada nunca vê a senha do gestor.
 */
public interface OauthCalendario {

    /**
     * @param codigo      código devolvido pelo provedor no redirect
     * @param redirectUri o mesmo usado na autorização (o provedor confere)
     * @throws ConsentimentoInvalido código expirado, já usado ou de outro app
     */
    Conta trocar(String codigo, String redirectUri);

    /**
     * URL para onde mandar o gestor autorizar. Montada no servidor porque é ele
     * que sabe o client id e o **escopo mínimo** pedido — a tela só redireciona.
     *
     * @param state valor opaco devolvido pelo provedor no retorno (anti-CSRF)
     */
    String urlConsentimento(String redirectUri, String state);

    /** 422 — o consentimento não vale; o gestor precisa autorizar de novo. */
    class ConsentimentoInvalido extends RuntimeException {
        public ConsentimentoInvalido(String msg) {
            super(msg);
        }
    }
}
