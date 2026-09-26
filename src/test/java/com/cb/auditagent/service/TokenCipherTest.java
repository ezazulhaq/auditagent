package com.cb.auditagent.service;

import org.junit.jupiter.api.Test;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.service.TokenCipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {
    @Test
    void encryptsWithRandomizedAuthenticatedEncryption() {
        GitHubAppConfig config = new GitHubAppConfig();
        config.setTokenEncryptionKey("test-only-32-byte-token-encryption-key");
        TokenCipher cipher = new TokenCipher(config);

        String first = cipher.encrypt("github-token");
        String second = cipher.encrypt("github-token");

        assertNotEquals("github-token", first);
        assertNotEquals(first, second);
        assertEquals("github-token", cipher.decrypt(first));
        char replacement = first.charAt(first.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = first.substring(0, first.length() - 1) + replacement;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }
}
