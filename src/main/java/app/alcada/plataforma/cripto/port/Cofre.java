package app.alcada.plataforma.cripto.port;

/**
 * Cifra segredos operacionais que precisam ser lidos de volta — token OAuth do
 * calendário do gestor, por exemplo (RFC-0009). Diferente do token de portal,
 * que é guardado só como hash: aqui a Alçada precisa **usar** o segredo depois,
 * então cifra em vez de resumir. Vazar o banco sem a chave não vaza os tokens.
 */
public interface Cofre {

    /** Cifra e devolve texto transportável (base64). */
    String cifrar(String claro);

    /** Decifra o que {@link #cifrar} produziu. */
    String decifrar(String cifrado);
}
