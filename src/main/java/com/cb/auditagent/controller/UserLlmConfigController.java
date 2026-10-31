package com.cb.auditagent.controller;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.dto.TestConnectionRequest;
import com.cb.auditagent.dto.TestConnectionResponse;
import com.cb.auditagent.dto.UserLlmConfigDto;
import com.cb.auditagent.dto.UserLlmConfigRequest;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.UserLlmConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/user/llm-config")
public class UserLlmConfigController {

    private final UserLlmConfigService configService;
    private final GitHubAuthService auth;

    public UserLlmConfigController(UserLlmConfigService configService, GitHubAuthService auth) {
        this.configService = configService;
        this.auth = auth;
    }

    @GetMapping
    public ResponseEntity<UserLlmConfigDto> getConfig(ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        return ResponseEntity.ok(configService.getConfig(user.userId()));
    }

    @PostMapping
    public ResponseEntity<UserLlmConfigDto> saveConfig(@RequestBody UserLlmConfigRequest request, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        return ResponseEntity.ok(configService.saveConfig(user.userId(), request));
    }

    @PostMapping("/test")
    public ResponseEntity<TestConnectionResponse> testConfig(@RequestBody TestConnectionRequest request, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        return ResponseEntity.ok(configService.testConfig(user.userId(), request));
    }
}

