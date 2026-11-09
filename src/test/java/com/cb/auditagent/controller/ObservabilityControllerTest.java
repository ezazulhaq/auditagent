package com.cb.auditagent.controller;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservabilityControllerTest {
    private DatabaseService databaseService;
    private GitHubAuthService authService;
    private ObservabilityController controller;
    private AuthenticatedUser sampleUser;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        authService = mock(GitHubAuthService.class);
        controller = new ObservabilityController(databaseService, authService);

        sampleUser = new AuthenticatedUser("user-1", 123L, "octocat", "Octocat", null, "octocat");
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/observability/tokens").build());
    }

    @Test
    void getTokenUsageReturnsRepoNameAndMetrics() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);
        Map<String, Object> row = Map.of(
                "repoName", "octocat/auditagent",
                "repoPath", "octocat/auditagent",
                "modelName", "gpt-4o",
                "totalTokens", 400L,
                "callCount", 2L
        );
        when(databaseService.getTokenUsageForUser(sampleUser.userId())).thenReturn(List.of(row));

        ResponseEntity<List<Map<String, Object>>> response = controller.getTokenUsage(exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals("octocat/auditagent", body.get(0).get("repoName"));
        assertEquals("gpt-4o", body.get(0).get("modelName"));
        assertEquals(400L, body.get(0).get("totalTokens"));
        assertEquals(2L, body.get(0).get("callCount"));
    }
}
