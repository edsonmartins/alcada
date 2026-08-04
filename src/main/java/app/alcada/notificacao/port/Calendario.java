package app.alcada.notificacao.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Saída para o calendário do gestor (RFC-0009), simétrica ao {@link Canal} e ao
 * {@link Email}: nenhum módulo de domínio conhece Google ou Microsoft. Criar
 * evento é efeito externo — sai pelo outbox, nunca dentro do request.
 */
public interface Calendario {

    /**
     * Cria o compromisso na agenda do gestor.
     *
     * @return id do evento no provedor; {@code null} se já existia (idempotente
     *         por {@code idempotencyKey}).
     * @throws CalendarioIndisponivel falha temporária — o outbox reprocessa.
     * @throws SemConta o gestor não conectou calendário: não adianta repetir.
     */
    String criarEvento(OrgId org, CriarEvento evento);

    /**
     * Tira o compromisso da agenda do gestor. Idempotente: evento já removido (ou
     * inexistente) não é erro — o estado desejado é o mesmo.
     *
     * @throws CalendarioIndisponivel falha temporária — o outbox reprocessa.
     * @throws SemConta o gestor desconectou o calendário; não adianta repetir.
     */
    void cancelarEvento(OrgId org, java.util.UUID gestorId, String eventoId);

    /** Falha de entrega — o efeito volta para retentativa no outbox (INV-13). */
    class CalendarioIndisponivel extends RuntimeException {
        public CalendarioIndisponivel(String msg) {
            super(msg);
        }
    }

    /** Não há calendário conectado para este gestor: impossível, não falho. */
    class SemConta extends RuntimeException {
        public SemConta(String msg) {
            super(msg);
        }
    }
}
