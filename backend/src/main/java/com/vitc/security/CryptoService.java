package com.vitc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM helper used to encrypt provider secrets before they are written
 * to the database.
 *
 * <p>The master key comes from the {@code RAZORPAY_ENCRYPTION_KEY} environment
 * variable (mapped to {@code app.security.encryption-key}). It is never stored
 * in the database and never leaves the server. When it is absent the
 * application still starts — a development-only key is derived so existing
 * installations keep working — but a warning is logged, and secrets encrypted
 * with the fallback key cannot be read once a real key is configured.</p>
 */
@Slf4j
@Component
public class CryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String DEV_FALLBACK = "vitc-dev-only-fallback-key-change-me";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${app.security.encryption-key:}") String configuredKey) {
        String material = configuredKey == null ? "" : configuredKey.trim();
        if (material.isEmpty()) {
            log.warn("RAZORPAY_ENCRYPTION_KEY is not set — falling back to a development key. "
                    + "Set it before storing production Razorpay credentials.");
            material = DEV_FALLBACK;
        }
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    /** Encrypts plain text; returns Base64(iv || ciphertext). */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt the payment secret", e);
        }
    }

    /** Decrypts a value produced by {@link #encrypt(String)}. */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to decrypt the stored payment secret — the encryption key may have changed. "
                            + "Re-save the credentials in Admin -> Payment Settings.", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
