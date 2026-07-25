package app.alcada.autonomia.internal;

/** Falhas de negócio do motor, mapeadas a códigos da docs/API.md. */
final class Falhas {

    private Falhas() {
    }

    /** 422 alcada.inelegivel — classe não elegível ao nível pedido (ADR-0004). */
    static class Inelegivel extends RuntimeException {
        Inelegivel(String msg) {
            super(msg);
        }
    }

    /** 409 janela.expirada — desfazer fora da janela de reversibilidade. */
    static class JanelaExpirada extends RuntimeException {
        JanelaExpirada(String msg) {
            super(msg);
        }
    }

    /** 409 pendencia.estado_invalido — transição não permitida. */
    static class EstadoInvalido extends RuntimeException {
        EstadoInvalido(String msg) {
            super(msg);
        }
    }

    /** 403 — ator não é o dono da delegação (ADR-0013, fronteira de autorização). */
    static class NaoAutorizado extends RuntimeException {
        NaoAutorizado(String msg) {
            super(msg);
        }
    }
}
