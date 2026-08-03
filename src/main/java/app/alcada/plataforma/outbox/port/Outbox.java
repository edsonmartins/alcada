package app.alcada.plataforma.outbox.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Porta de publicação do outbox. A escrita participa da MESMA transação da
 * transição de estado do chamador — se a transação reverte, nada é publicado.
 */
public interface Outbox {

    /**
     * Enfileira um efeito externo. Idempotente por {@code idempotencyKey}:
     * reenfileirar a mesma chave não cria duplicata.
     */
    void publicar(MensagemOutbox mensagem);

    /**
     * Enfileira um efeito externo que só pode sair <b>depois</b> de {@code
     * disponivelEm} (INV-14): a janela de arrependimento. Enquanto não vence, a
     * linha fica no outbox e pode ser descartada — o terceiro nunca soube.
     */
    void publicarApos(MensagemOutbox mensagem, java.time.OffsetDateTime disponivelEm);

    /**
     * Descarta um efeito ainda não emitido, pela chave de idempotência. Só apaga
     * o que está PENDENTE: o que já saiu não volta atrás (INV-14).
     *
     * @return true se o efeito foi descartado antes de sair
     */
    boolean descartarPendente(OrgId org, String idempotencyKey);

    /**
     * Libera os efeitos represados de um trajeto (023): as linhas ganham
     * {@code trajeto_id = NULL} e passam a ser emitidas pelo worker. Chamado ao
     * estacionar + confirmar o resumo (INV-14). Escopado por org (INV-15).
     */
    void liberarTrajeto(OrgId org, UUID trajetoId);

    /**
     * Descarta o efeito externo represado de UMA pendência de um trajeto (023, C4):
     * o "desfazer por item" no resumo ao estacionar — o terceiro nunca é
     * comunicado. As demais pendências do trajeto seguem represadas.
     */
    void descartarTrajeto(OrgId org, UUID trajetoId, UUID pendenciaId);
}
