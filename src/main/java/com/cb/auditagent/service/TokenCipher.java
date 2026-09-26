package com.cb.auditagent.service;

import org.springframework.stereotype.Service;

import com.cb.auditagent.config.GitHubAppConfig;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GitHubAppConfig config;

    public TokenCipher(GitHubAppConfig config) {
        this.config = config;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank())
            return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt credential", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank())
            return null;
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext);
            if (packed.length < 29)
                throw new IllegalArgumentException("Encrypted credential is invalid");
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt credential", e);
        }
    }

    private SecretKeySpec key() throws Exception {
        String material = config.getTokenEncryptionKey();
        if (material == null || material.isBlank()) {
            throw new IllegalStateException("AUDITAGENT_TOKEN_ENCRYPTION_KEY is required");
        }
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
