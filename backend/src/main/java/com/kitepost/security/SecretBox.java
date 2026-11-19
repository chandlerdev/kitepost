package com.kitepost.security;

import com.kitepost.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the mailbox passwords kept in the database with AES-256-GCM. The key is never stored in the
 * database: it comes from the server setting MAIL_ENCRYPTION_KEY (a long random value). Without it,
 * passwords cannot be saved. Changing the key makes the saved passwords unreadable (enter them again).
 * Stored form: "v1:" + base64(12-byte random IV + ciphertext + 16-byte tag).
 */
@Component
public class SecretBox {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    @Autowired
    public SecretBox(AppProperties props) { this(props.security().encryptionKey()); }

    public SecretBox(String encryptionKey) {
        this.key = AppProperties.has(encryptionKey) ? new SecretKeySpec(sha256(encryptionKey.trim()), "AES") : null;
    }

    /** False when MAIL_ENCRYPTION_KEY is not set. */
    public boolean available() { return key != null; }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        if (key == null) throw new IllegalStateException("MAIL_ENCRYPTION_KEY is not set on the server.");
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return "v1:" + Base64.getEncoder().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the password.", e);
        }
    }

    /** The plain text, or null when it is empty, the key is missing, or the key does not match. */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        if (key == null || !stored.startsWith("v1:")) return null;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
