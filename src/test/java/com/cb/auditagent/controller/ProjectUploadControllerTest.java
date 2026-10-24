package com.cb.auditagent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.ProjectUploadService;

class ProjectUploadControllerTest {

    private final AuthenticatedUser user = new AuthenticatedUser("github:42", 42, "tester", "Tester", "", "csrf-123");
    private ProjectUploadService projectUploadService;
    private GitHubAuthService auth;
    private ProjectUploadController controller;

    @BeforeEach
    void setUp() {
        projectUploadService = mock(ProjectUploadService.class);
        auth = mock(GitHubAuthService.class);
        controller = new ProjectUploadController(projectUploadService, auth);
    }

    @Test
    void testGetUploadReport() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/reports/upload?uploadId=up-1"));
        when(auth.requireUser(exchange)).thenReturn(user);
        when(projectUploadService.getUploadReport(user, "up-1")).thenReturn(Map.of("uploadId", "up-1", "projectName", "my-app"));

        ResponseEntity<?> response = controller.getUploadReport("up-1", exchange);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("up-1", body.get("uploadId"));
    }

    @Test
    void testExportUploadReport() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/reports/upload/export?uploadId=up-1&format=pdf"));
        when(auth.requireUser(exchange)).thenReturn(user);
        when(projectUploadService.exportUploadReport(user, "up-1", "pdf")).thenReturn(
                new ReportArtifact("PDF_CONTENT".getBytes(StandardCharsets.UTF_8), "application/pdf", "my-app-report.pdf"));

        ResponseEntity<byte[]> response = controller.exportUploadReport("up-1", "pdf", exchange);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("PDF_CONTENT", new String(response.getBody(), StandardCharsets.UTF_8));
    }
}

