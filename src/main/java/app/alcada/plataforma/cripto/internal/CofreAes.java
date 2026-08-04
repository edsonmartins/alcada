package app.alcada.plataforma.cripto.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import app.alcada.plataforma.cripto.port.Cofre;
import io.quarkus.runtime.configuration.ConfigurationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * AES-GCM com chave de 256 bits vinda da configuração ({@code alcada.cripto.chave},
 * base64). O nonce é sorteado por operação e viaja junto do texto cifrado.
 *
 * <p>Em {@code prod} a chave é <b>obrigatória</b> — sem ela a aplicação não sobe,
 * porque subir com uma chave de desenvolvimento cifraria tokens reais com segredo
 * público. Fora de prod, uma chave fixa de desenvolvimento mantém dev/test
 * funcionando sem configuração.
 */
@ApplicationScoped
public class CofreAes implements Cofre {

    private static final int TAMANHO_NONCE = 12;
    private static final int TAMANHO_TAG = 128;
    /** Chave só de desenvolvimento: nunca protege dado real (prod exige a sua). */
    private static final String CHAVE_DEV = "ZGV2LWFsY2FkYS1jb2ZyZS1jaGF2ZS0zMi1ieXRlcyE=";

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public CofreAes(
            @ConfigProperty(name = "alcada.cripto.chave") Optional<String> chaveBase64,
            @ConfigProperty(name = "quarkus.profile", defaultValue = "dev") String perfil,
            @ConfigProperty(name = "alcada.calendario.real", defaultValue = "false") boolean real) {
        // Onde há segredo de verdade a guardar (prod, ou calendário real ligado no
        // piloto), a chave é obrigatória — a de desenvolvimento está no código.
        if (chaveBase64.isEmpty() && ("prod".equals(perfil) || real)) {
            throw new ConfigurationException(
                    "alcada.cripto.chave é obrigatória quando há segredo real a guardar "
                    + "(32 bytes em base64)");
        }
        byte[] bytes = Base64.getDecoder().decode(chaveBase64.orElse(CHAVE_DEV));
        if (bytes.length != 32) {
            throw new ConfigurationException("alcada.cripto.chave deve ter 32 bytes (256 bits)");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String cifrar(String claro) {
        try {
            byte[] nonce = new byte[TAMANHO_NONCE];
            random.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, nonce));
            byte[] cifrado = c.doFinal(claro.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, saida, 0, nonce.length);
            System.arraycopy(cifrado, 0, saida, nonce.length, cifrado.length);
            return Base64.getEncoder().encodeToString(saida);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao cifrar", e);
        }
    }

    @Override
    public String decifrar(String cifrado) {
        try {
            byte[] bytes = Base64.getDecoder().decode(cifrado);
            byte[] nonce = new byte[TAMANHO_NONCE];
            System.arraycopy(bytes, 0, nonce, 0, TAMANHO_NONCE);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, nonce));
            byte[] claro = c.doFinal(bytes, TAMANHO_NONCE, bytes.length - TAMANHO_NONCE);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao decifrar", e);
        }
    }
}
