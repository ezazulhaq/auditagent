package com.cb.auditagent.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.GitHubAuthService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiSecurityFilter implements WebFilter {
    private final GitHubAuthService auth;

    public ApiSecurityFilter(GitHubAuthService auth) {
        this.auth = auth;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/") || exchange.getRequest().getMethod() == HttpMethod.OPTIONS
                || path.startsWith("/api/auth/github/") || path.equals("/api/auth/session")
                || path.equals("/api/webhooks/github"))
            return chain.filter(exchange);
        return Mono.fromCallable(() -> auth.authenticate(exchange)).subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user.isEmpty())
                        return reject(exchange, HttpStatus.UNAUTHORIZED, "Authentication required");
                    AuthenticatedUser authenticated = user.get();
                    exchange.getAttributes().put(GitHubAuthService.USER_ATTRIBUTE, authenticated);
                    HttpMethod method = exchange.getRequest().getMethod();
                    if (method != HttpMethod.GET && method != HttpMethod.HEAD
                            && !auth.csrfMatches(authenticated,
                                    exchange.getRequest().getHeaders().getFirst("X-CSRF-Token"))) {
                        return reject(exchange, HttpStatus.FORBIDDEN, "CSRF token is invalid");
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
