package com.cb.auditagent.controller;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.dto.TestConnectionRequest;
import com.cb.auditagent.dto.TestConnectionResponse;
import com.cb.auditagent.dto.UserLlmConfigDto;
import com.cb.auditagent.dto.UserLlmConfigRequest;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.UserLlmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLlmConfigControllerTest {

    private UserLlmConfigService configService;
    private GitHubAuthService authService;
    private UserLlmConfigController controller;
    private AuthenticatedUser testUser;
    private ServerWebExchange exchange;

    @BeforeEach
    void setup() {
        configService = mock(UserLlmConfigService.class);
        authService = mock(GitHubAuthService.class);
        controller = new UserLlmConfigController(configService, authService);

        testUser = new AuthenticatedUser("user-123", 12345L, "octocat", "Octo Cat", "https://avatar.url", "csrf-token");
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/user/llm-config").build());

        when(authService.requireUser(any())).thenReturn(testUser);
    }

    @Test
    void testGetConfig() {
        UserLlmConfigDto dto = new UserLlmConfigDto(
                true, true, "OPENROUTER", "https://openrouter.ai/api/v1", "anthropic/claude-3.5-sonnet", null, "sk-••••••••3a8f"
        );
        when(configService.getConfig("user-123")).thenReturn(dto);

        ResponseEntity<UserLlmConfigDto> response = controller.getConfig(exchange);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(dto, response.getBody());
    }

    @Test
    void testSaveConfig() {
        UserLlmConfigRequest request = new UserLlmConfigRequest(
                "OPENROUTER", "https://openrouter.ai/api/v1", "sk-new-key", "gpt-4o", null
        );
        UserLlmConfigDto updatedDto = new UserLlmConfigDto(
                true, true, "OPENROUTER", "https://openrouter.ai/api/v1", "gpt-4o", null, "sk-••••••••-key"
        );
        when(configService.saveConfig("user-123", request)).thenReturn(updatedDto);

        ResponseEntity<UserLlmConfigDto> response = controller.saveConfig(request, exchange);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(updatedDto, response.getBody());
    }

    @Test
    void testTestConfig() {
        TestConnectionRequest request = new TestConnectionRequest(
                "OPENROUTER", "https://openrouter.ai/api/v1", "sk-test", "gpt-4o"
        );
        TestConnectionResponse expected = TestConnectionResponse.ok("Connection successful");
        when(configService.testConfig("user-123", request)).thenReturn(expected);

        ResponseEntity<TestConnectionResponse> response = controller.testConfig(request, exchange);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
    }
}

