package com.cb.auditagent.service;

import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.GitHubAuthorization;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

@Service
public class GitHubAuthService {
    public static final String SESSION_COOKIE = "AUDITAGENT_SESSION";
    public static final String OAUTH_STATE_COOKIE = "AUDITAGENT_OAUTH_STATE";
    public static final String USER_ATTRIBUTE = GitHubAuthService.class.getName() + ".user";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitHubAppConfig config;
    private final DatabaseService database;
    private final TokenCipher cipher;
    private final GitHubApiClient api;

    public GitHubAuthService(GitHubAppConfig config, DatabaseService database,
            TokenCipher cipher, GitHubApiClient api) {
        this.config = config;
        this.database = database;
        this.cipher = cipher;
        this.api = api;
    }

    public LoginStart beginLogin() {
        requireConfigured();
        String state = randomToken(32);
        String verifier = randomToken(64);
        String challenge = base64Url(sha256Bytes(verifier));
        database.saveOAuthState(sha256(state), cipher.encrypt(verifier),
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        String authorizeUrl = config.getAuthorizeUrl() + "?client_id=" + encode(config.getClientId()) +
                "&redirect_uri=" + encode(config.getCallbackUrl()) + "&state=" + encode(state) +
                "&code_challenge=" + encode(challenge) + "&code_challenge_method=S256";
        return new LoginStart(authorizeUrl, state);
    }

    public LoginResult completeLogin(String code, String state, String browserState) {
        requireConfigured();
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("GitHub callback is missing code or state");
        }
        if (browserState == null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                browserState.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("GitHub login state does not match the initiating browser");
        }
        String encryptedVerifier = database.consumeOAuthState(sha256(state))
                .orElseThrow(() -> new IllegalArgumentException("GitHub login state is invalid or expired"));
        GitHubApiClient.OAuthTokens tokens = api.exchangeCode(code, cipher.decrypt(encryptedVerifier));
        GitHubApiClient.UserProfile profile = api.getUser(tokens.accessToken());
        String userId = database.upsertGitHubUser(profile.id(), profile.login(), profile.name(), profile.avatarUrl());
        database.saveGitHubAuthorization(new GitHubAuthorization(userId, cipher.encrypt(tokens.accessToken()),
                tokens.accessExpiresAt(), cipher.encrypt(tokens.refreshToken()), tokens.refreshExpiresAt()));
        String rawSession = randomToken(48);
        String csrf = randomToken(32);
        database.createUserSession(sha256(rawSession), userId, csrf,
                LocalDateTime.now(ZoneOffset.UTC).plusHours(config.getSessionHours()));
        AuthenticatedUser user = new AuthenticatedUser(userId, profile.id(), profile.login(), profile.name(),
                profile.avatarUrl(), csrf);
        return new LoginResult(rawSession, user);
    }

    public Optional<AuthenticatedUser> authenticate(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        if (cookie == null || cookie.getValue().isBlank())
            return Optional.empty();
        return database.findAuthenticatedUser(sha256(cookie.getValue()));
    }

    public AuthenticatedUser requireUser(ServerWebExchange exchange) {
        Object attached = exchange.getAttribute(USER_ATTRIBUTE);
        if (attached instanceof AuthenticatedUser user)
            return user;
        return authenticate(exchange).orElseThrow(() -> new SecurityException("Authentication required"));
    }

    public synchronized String accessToken(String userId) {
        GitHubAuthorization authorization = database.getGitHubAuthorization(userId)
                .orElseThrow(() -> new SecurityException("GitHub authorization is missing"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        if (authorization.accessExpiresAt() == null || authorization.accessExpiresAt().isAfter(now)) {
            return cipher.decrypt(authorization.accessTokenEncrypted());
        }
        if (authorization.refreshTokenEncrypted() == null || authorization.refreshExpiresAt() == null
                || !authorization.refreshExpiresAt().isAfter(now)) {
            throw new SecurityException("GitHub authorization expired; sign in again");
        }
        GitHubApiClient.OAuthTokens refreshed = api.refresh(cipher.decrypt(authorization.refreshTokenEncrypted()));
        database.saveGitHubAuthorization(new GitHubAuthorization(userId, cipher.encrypt(refreshed.accessToken()),
                refreshed.accessExpiresAt(), cipher.encrypt(refreshed.refreshToken()), refreshed.refreshExpiresAt()));
        return refreshed.accessToken();
    }

    public void logout(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        if (cookie != null)
            database.deleteUserSession(sha256(cookie.getValue()));
    }

    public boolean csrfMatches(AuthenticatedUser user, String supplied) {
        return supplied != null && MessageDigest.isEqual(user.csrfToken().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    private void requireConfigured() {
        if (!config.isConfigured())
            throw new IllegalStateException("GitHub App authentication is not configured");
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base64Url(value);
    }

    private String sha256(String value) {
        return base64Url(sha256Bytes(value));
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record LoginResult(String sessionToken, AuthenticatedUser user) {
    }

    public record LoginStart(String authorizeUrl, String browserState) {
    }
}
