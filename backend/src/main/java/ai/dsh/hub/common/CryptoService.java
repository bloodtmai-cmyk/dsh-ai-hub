package ai.dsh.hub.common;

import ai.dsh.hub.config.AiHubProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private static final String VERSION = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(AiHubProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.security().masterKey());
        if (decoded.length != 32) {
            throw new IllegalStateException("AI_HUB_MASTER_KEY must decode to exactly 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt protected content", exception);
        }
    }

    public String decrypt(String encoded, String associatedData) {
        if (!encoded.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported encrypted content version");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring(VERSION.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt protected content", exception);
        }
    }

    public String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
