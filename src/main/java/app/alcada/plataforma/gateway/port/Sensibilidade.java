package app.alcada.plataforma.gateway.port;

/**
 * Sensibilidade declarada pelo chamador (RFC-0007). O chamador declara; o
 * gateway decide o destino. O chamador nunca escolhe provedor nem modelo.
 */
public enum Sensibilidade {
    /** Conteúdo já público (classificação de tipo, sumarização pública). */
    PUBLICA,
    /** Extração a partir de mensagem — sai só após o minimizador. */
    INTERNA,
    /** Áudio do gestor, valores de contrato, dossiê, avaliação de parceiro — local, sempre. */
    RESTRITA
}
