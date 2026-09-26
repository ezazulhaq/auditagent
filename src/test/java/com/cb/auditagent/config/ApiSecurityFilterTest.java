package com.cb.auditagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import com.cb.auditagent.config.ApiSecurityFilter;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.GitHubAuthService;

import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiSecurityFilterTest {
    @Test
    void blocksMutationWithoutMatchingCsrfToken() {
        GitHubAuthService auth = mock(GitHubAuthService.class);
        AuthenticatedUser user = new AuthenticatedUser("github:1", 1, "octo", "Octo", "", "csrf");
        ApiSecurityFilter filter = new ApiSecurityFilter(auth);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/runs/run-1/decision").build());
        WebFilterChain chain = mock(WebFilterChain.class);
        when(auth.authenticate(exchange)).thenReturn(Optional.of(user));
        when(auth.csrfMatches(user, null)).thenReturn(false);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(exchange);
    }

    @Test
    void allowsAuthenticatedMutationWithMatchingCsrfToken() {
        GitHubAuthService auth = mock(GitHubAuthService.class);
        AuthenticatedUser user = new AuthenticatedUser("github:1", 1, "octo", "Octo", "", "csrf");
        ApiSecurityFilter filter = new ApiSecurityFilter(auth);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/runs/run-1/decision").header("X-CSRF-Token", "csrf").build());
        WebFilterChain chain = mock(WebFilterChain.class);
        when(auth.authenticate(exchange)).thenReturn(Optional.of(user));
        when(auth.csrfMatches(user, "csrf")).thenReturn(true);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
