package com.cb.auditagent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final DatabaseService databaseService;
    private final GitHubAuthService authService;

    public ObservabilityController(DatabaseService databaseService, GitHubAuthService authService) {
        this.databaseService = databaseService;
        this.authService = authService;
    }

    @GetMapping("/tokens")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getTokenUsage(ServerWebExchange exchange) {
        AuthenticatedUser user = authService.requireUser(exchange);
        List<Map<String, Object>> usage = databaseService.getTokenUsageForUser(user.userId());
        return Mono.just(ResponseEntity.ok(usage));
    }
}
