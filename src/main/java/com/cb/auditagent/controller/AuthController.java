package com.cb.auditagent.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.service.GitHubAuthService;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final GitHubAuthService auth;
    private final GitHubAppConfig config;

    public AuthController(GitHubAuthService auth, GitHubAppConfig config) {
        this.auth = auth;
        this.config = config;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(ServerWebExchange exchange) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("configured", auth.isConfigured());
        response.put("installationUrl", config.getInstallationUrl());
        auth.authenticate(exchange).ifPresentOrElse(user -> {
            response.put("authenticated", true);
            response.put("user", Map.of("id", user.userId(), "login", user.login(),
                    "name", user.displayName() == null ? user.login() : user.displayName(),
                    "avatarUrl", user.avatarUrl() == null ? "" : user.avatarUrl()));
            response.put("csrfToken", user.csrfToken());
        }, () -> response.put("authenticated", false));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .body(response);
    }

    @GetMapping("/github/login")
    public ResponseEntity<Void> login() {
        GitHubAuthService.LoginStart start = auth.beginLogin();
        ResponseCookie stateCookie = ResponseCookie
                .from(GitHubAuthService.OAUTH_STATE_COOKIE, start.browserState())
                .httpOnly(true).secure(config.useSecureCookies()).sameSite("Lax")
                .path("/api/auth/github")
                .maxAge(Duration.ofMinutes(10)).build();
        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .location(URI.create(start.authorizeUrl())).build();
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state,
            ServerWebExchange exchange) {
        HttpCookie stateCookie = exchange.getRequest().getCookies()
                .getFirst(GitHubAuthService.OAUTH_STATE_COOKIE);
        GitHubAuthService.LoginResult result = auth.completeLogin(code, state,
                stateCookie == null ? null : stateCookie.getValue());
        ResponseCookie cookie = ResponseCookie.from(GitHubAuthService.SESSION_COOKIE, result.sessionToken())
                .httpOnly(true).secure(config.useSecureCookies()).sameSite("Lax").path("/")
                .maxAge(Duration.ofHours(config.getSessionHours())).build();
        ResponseCookie expiredState = ResponseCookie.from(GitHubAuthService.OAUTH_STATE_COOKIE, "")
                .httpOnly(true).secure(config.useSecureCookies()).sameSite("Lax")
                .path("/api/auth/github")
                .maxAge(Duration.ZERO).build();
        exchange.getResponse().addCookie(cookie);
        exchange.getResponse().addCookie(expiredState);
        return ResponseEntity.status(302)
                .location(URI.create(config.getFrontendUrl())).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(ServerWebExchange exchange) {
        auth.logout(exchange);
        ResponseCookie expired = ResponseCookie.from(GitHubAuthService.SESSION_COOKIE, "")
                .httpOnly(true).secure(config.useSecureCookies()).sameSite("Lax").path("/")
                .maxAge(Duration.ZERO).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(Map.of("authenticated", false));
    }
}
