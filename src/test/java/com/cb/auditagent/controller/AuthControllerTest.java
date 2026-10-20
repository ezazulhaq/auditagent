package com.cb.auditagent.controller;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.GitHubAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    private GitHubAuthService auth;
    private GitHubAppConfig config;
    private AuthController controller;

    @BeforeEach
    void setup() {
        auth = mock(GitHubAuthService.class);
        config = new GitHubAppConfig();
        config.setFrontendUrl("https://auditagent-dev.chotabuziness.com");
        config.setCallbackUrl("https://auditagent-dev.chotabuziness.com/api/auth/github/callback");
        config.setSessionHours(24);
        controller = new AuthController(auth, config);
    }

    @Test
    void loginSetsStateCookieAndRedirects() {
        when(auth.beginLogin()).thenReturn(new GitHubAuthService.LoginStart(
                "https://github.com/login/oauth/authorize?state=abc", "abc"));

        ResponseEntity<Void> response = controller.login();
        assertEquals(302, response.getStatusCode().value());
        assertEquals("https://github.com/login/oauth/authorize?state=abc",
                response.getHeaders().getLocation().toString());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie != null && cookie.contains("AUDITAGENT_OAUTH_STATE=abc"));
        assertTrue(cookie.contains("Path=/api/auth/github"));
    }

    @Test
    void callbackSetsSessionCookieWhenStateMatches() {
        AuthenticatedUser user = new AuthenticatedUser("u1", 123L, "octocat", "The Octocat", "https://avatar", "csrf-123");
        when(auth.completeLogin("code123", "state123", "state123"))
                .thenReturn(new GitHubAuthService.LoginResult("session-token-xyz", user));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/github/callback?code=code123&state=state123")
                .cookie(new HttpCookie(GitHubAuthService.OAUTH_STATE_COOKIE, "state123"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseEntity<Void> response = controller.callback("code123", "state123", exchange);
        assertEquals(302, response.getStatusCode().value());
        assertEquals("https://auditagent-dev.chotabuziness.com", response.getHeaders().getLocation().toString());

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertTrue(setCookies != null && setCookies.size() >= 2);
        assertTrue(setCookies.stream().anyMatch(c -> c.contains("AUDITAGENT_SESSION=session-token-xyz")));
        assertTrue(setCookies.stream().anyMatch(c -> c.contains("AUDITAGENT_OAUTH_STATE=;")));
    }

    @Test
    void callbackPropagatesSecurityExceptionOnStateMismatch() {
        when(auth.completeLogin("code123", "state123", "other-state"))
                .thenThrow(new SecurityException("GitHub login state does not match the initiating browser"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/github/callback?code=code123&state=state123")
                .cookie(new HttpCookie(GitHubAuthService.OAUTH_STATE_COOKIE, "other-state"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertThrows(SecurityException.class, () -> controller.callback("code123", "state123", exchange));
    }

    @Test
    void callbackPropagatesIllegalStateExceptionWithoutMasking() {
        when(auth.completeLogin("bad_code", "state123", "state123"))
                .thenThrow(new IllegalStateException("GitHub authorization failed: bad_verification_code"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/github/callback?code=bad_code&state=state123")
                .cookie(new HttpCookie(GitHubAuthService.OAUTH_STATE_COOKIE, "state123"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> controller.callback("bad_code", "state123", exchange));
        assertTrue(ex.getMessage().contains("bad_verification_code"));
    }
}
