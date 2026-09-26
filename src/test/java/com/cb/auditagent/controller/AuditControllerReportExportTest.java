package com.cb.auditagent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.controller.AuditController;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.service.*;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditControllerReportExportTest {
    @Test
    void returnsAProtectedTypedAttachmentWithTheServerGeneratedFilename() {
        ScanOrchestratorService remediation = mock(ScanOrchestratorService.class);
        GitHubAuthService auth = mock(GitHubAuthService.class);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        AuthenticatedUser user = new AuthenticatedUser("github:99", 99, "octocat", "Octo", "", "csrf");
        byte[] content = "pdf".getBytes(StandardCharsets.UTF_8);
        when(auth.requireUser(exchange)).thenReturn(user);
        when(remediation.exportReport(user, 42, "main", "pdf"))
                .thenReturn(new ReportArtifact(content, "application/pdf", "repo-main-security-report.pdf"));
        AuditController controller = new AuditController(mock(SkillManagerService.class), mock(LlmService.class),
                remediation, mock(RemediationWorkflowService.class), mock(RepositoryAccessService.class), auth,
                mock(DatabaseService.class));

        ResponseEntity<byte[]> response = controller.exportReport(42, "main", "pdf", exchange);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("repo-main-security-report.pdf", response.getHeaders().getContentDisposition().getFilename());
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertArrayEquals(content, response.getBody());
    }
}
