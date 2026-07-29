package app.alcada.captura.port;

import java.util.List;

/**
 * Envelope de entrada normalizado (ADR-0021). O Linktor entrega isto — com o
 * trecho novo da thread já isolado — e a Alçada referencia o bruto por
 * {@code mensagemId} sem duplicá-lo. Webhooks de sistema usam o mesmo envelope.
 */
public record MensagemRecebida(
        String canal,          // WHATSAPP | EMAIL | WEBHOOK
        String fonteId,        // fonte declarada (ADR-0011)
        String autorExt,       // autor no canal — em grupo, o INDIVÍDUO que falou
        String threadRef,      // fio; em grupo = grupoId (thread pelo grupo)
        String texto,          // trecho novo já isolado
        List<String> anexosRef,
        String mensagemId,     // referência ao bruto no Linktor; idempotência
        boolean grupo,         // 024: veio de conversa de grupo?
        String grupoId) {      // 024: id do grupo (chat_jid); null em 1:1
}
