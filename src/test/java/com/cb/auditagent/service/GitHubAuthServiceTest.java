package com.cb.auditagent.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.GitHubAuthorization;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.TokenCipher;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubAuthServiceTest {
    @Test
    void createsPkceLoginAndStoresOnlyHashedStateAndEncryptedVerifier() throws Exception {
        GitHubAppConfig config = configured();
        DatabaseService database = mock(DatabaseService.class);
        GitHubApiClient api = mock(GitHubApiClient.class);
        TokenCipher cipher = new TokenCipher(config);
        GitHubAuthService auth = new GitHubAuthService(config, database, cipher, api);

        GitHubAuthService.LoginStart start = auth.beginLogin();
        URI login = URI.create(start.authorizeUrl());
        Map<String, String> query = query(login);
        assertEquals("S256", query.get("code_challenge_method"));
        assertFalse(query.get("state").isBlank());
        assertEquals(query.get("state"), start.browserState());

        ArgumentCaptor<String> verifierCiphertext = ArgumentCaptor.forClass(String.class);
        verify(database).saveOAuthState(eq(hash(query.get("state"))), verifierCiphertext.capture(), any());
        String verifier = cipher.decrypt(verifierCiphertext.getValue());
        assertNotEquals(verifier, verifierCiphertext.getValue());
        assertEquals(base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII))), query.get("code_challenge"));
    }

    @Test
    void callbackPersistsEncryptedExpiringAuthorizationAndHashedSession() throws Exception {
        GitHubAppConfig config = configured();
        DatabaseService database = mock(DatabaseService.class);
        GitHubApiClient api = mock(GitHubApiClient.class);
        TokenCipher cipher = new TokenCipher(config);
        GitHubAuthService auth = new GitHubAuthService(config, database, cipher, api);
        String rawState = "state";
        String verifier = "verifier";
        when(database.consumeOAuthState(hash(rawState))).thenReturn(Optional.of(cipher.encrypt(verifier)));
        LocalDateTime accessExpiry = LocalDateTime.now().plusHours(8);
        LocalDateTime refreshExpiry = LocalDateTime.now().plusMonths(6);
        when(api.exchangeCode("code", verifier)).thenReturn(
                new GitHubApiClient.OAuthTokens("access-secret", accessExpiry, "refresh-secret", refreshExpiry));
        when(api.getUser("access-secret")).thenReturn(
                new GitHubApiClient.UserProfile(99, "octocat", "Octo Cat", "https://avatars.test/99"));
        when(database.upsertGitHubUser(99, "octocat", "Octo Cat", "https://avatars.test/99"))
                .thenReturn("github:99");

        GitHubAuthService.LoginResult result = auth.completeLogin("code", rawState, rawState);

        ArgumentCaptor<GitHubAuthorization> authorization = ArgumentCaptor.forClass(GitHubAuthorization.class);
        verify(database).saveGitHubAuthorization(authorization.capture());
        assertEquals("access-secret", cipher.decrypt(authorization.getValue().accessTokenEncrypted()));
        assertEquals("refresh-secret", cipher.decrypt(authorization.getValue().refreshTokenEncrypted()));
        assertNotEquals("access-secret", authorization.getValue().accessTokenEncrypted());
        ArgumentCaptor<String> sessionHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> csrf = ArgumentCaptor.forClass(String.class);
        verify(database).createUserSession(sessionHash.capture(), eq("github:99"), csrf.capture(), any());
        assertEquals(hash(result.sessionToken()), sessionHash.getValue());
        assertTrue(csrf.getValue().length() >= 32);
    }

    @Test
    void callbackRejectsStateFromAnotherBrowserBeforeConsumingIt() {
        GitHubAppConfig config = configured();
        DatabaseService database = mock(DatabaseService.class);
        GitHubAuthService auth = new GitHubAuthService(config, database, new TokenCipher(config),
                mock(GitHubApiClient.class));

        assertThrows(SecurityException.class,
                () -> auth.completeLogin("code", "callback-state", "other-browser-state"));
        verify(database, never()).consumeOAuthState(any());
    }

    private GitHubAppConfig configured() {
        GitHubAppConfig config = new GitHubAppConfig();
        config.setAppId("1");
        config.setClientId("client");
        config.setClientSecret("client-secret");
        config.setPrivateKey("private-key-path");
        config.setWebhookSecret("webhook-secret");
        config.setTokenEncryptionKey("test-only-32-byte-token-encryption-key");
        config.setCallbackUrl("https://audit.test/api/auth/github/callback");
        return config;
    }

    private Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(value -> value.split("=", 2))
                .collect(Collectors.toMap(parts -> decode(parts[0]), parts -> decode(parts[1])));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String hash(String value) throws Exception {
        return base64Url(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
