package app.alcada.captura.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verificação HMAC-SHA256 do webhook do Linktor (padrão do vendax.ai):
 * assina {@code timestamp + "." + body}, hex lowercase, comparação em tempo
 * constante, tolerância de 300s (anti-replay). Fail-closed. O segredo é
 * credencial — nunca vaza em log, trilha ou mensagem de erro.
 */
final class AssinaturaHmac {

    static final long TOLERANCIA_SEGUNDOS = 300;

    private AssinaturaHmac() {
    }

    static boolean valida(String segredo, String timestamp, String body, String assinaturaHex, long agoraEpoch) {
        if (segredo == null || timestamp == null || assinaturaHex == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(agoraEpoch - ts) > TOLERANCIA_SEGUNDOS) {
            return false; // fora da janela — replay
        }
        String esperada = calcular(segredo, timestamp + "." + body);
        // comparação em tempo constante (não vaza por canal lateral de tempo)
        return MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8),
                assinaturaHex.trim().getBytes(StandardCharsets.UTF_8));
    }

    static String calcular(String segredo, String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] d = mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
