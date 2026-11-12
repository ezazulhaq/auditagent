package com.cb.auditagent.controller;

import com.cb.auditagent.domain.ApprovalPreview;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.dto.RunDecisionRequest;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.RemediationWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class RunPublicationControllerTest {
    private RemediationWorkflowService remediationWorkflow;
    private GitHubAuthService auth;
    private RunPublicationController controller;

    private AuthenticatedUser sampleUser;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        remediationWorkflow = mock(RemediationWorkflowService.class);
        auth = mock(GitHubAuthService.class);
        controller = new RunPublicationController(remediationWorkflow, auth);

        sampleUser = new AuthenticatedUser("user-1", 123L, "octocat", "Octocat", null, "octocat");
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/runs").build());
        when(auth.requireUser(exchange)).thenReturn(sampleUser);
    }

    @Test
    void getActiveRunForVulnerabilityReturnsMappedRun() {
        Map<String, Object> expected = Map.of(
                "run", Map.of("runId", "run-123", "status", "AWAITING_APPROVAL"),
                "publication", Map.of("pullRequestNumber", 42)
        );
        when(remediationWorkflow.findActiveRunForVulnerability(sampleUser, "VULN-000001")).thenReturn(expected);

        Map<String, Object> actual = controller.getActiveRunForVulnerability("VULN-000001", exchange);

        assertEquals(expected, actual);
        verify(remediationWorkflow).findActiveRunForVulnerability(sampleUser, "VULN-000001");
    }

    @Test
    void getActiveRunForVulnerabilityReturnsEmptyWhenNotFound() {
        when(remediationWorkflow.findActiveRunForVulnerability(sampleUser, "VULN-NONE")).thenReturn(Map.of());

        Map<String, Object> actual = controller.getActiveRunForVulnerability("VULN-NONE", exchange);

        assertNotNull(actual);
        assertEquals(0, actual.size());
        verify(remediationWorkflow).findActiveRunForVulnerability(sampleUser, "VULN-NONE");
    }

    @Test
    void previewReturnsApprovalPreview() {
        ApprovalPreview preview = new ApprovalPreview("run-123", "VULN-000001", "octo/repo", "main", "sha-1",
                "fix/vuln-1", List.of("src/App.java"), "--- diff", Map.of("tests", "passed"), "Fixed bug", "digest-1");
        when(remediationWorkflow.approvalPreview(sampleUser, "run-123")).thenReturn(preview);

        ApprovalPreview actual = controller.preview("run-123", exchange);

        assertEquals(preview, actual);
        verify(remediationWorkflow).approvalPreview(sampleUser, "run-123");
    }

    @Test
    void decisionStreamsEvents() {
        RunDecisionRequest request = new RunDecisionRequest();
        request.setVulnId("VULN-000001");
        request.setDecision("APPROVE_AND_CREATE_PR");
        request.setApprovalDigest("digest-1");
        request.setReason("looks good");

        when(remediationWorkflow.decide(sampleUser, "run-123", "VULN-000001", "APPROVE_AND_CREATE_PR", "digest-1", "looks good"))
                .thenReturn(Flux.just("event-1", "event-2"));

        Flux<String> result = controller.decision("run-123", request, exchange);

        List<String> events = result.collectList().block();
        assertEquals(List.of("event-1", "event-2"), events);
        verify(remediationWorkflow).decide(sampleUser, "run-123", "VULN-000001", "APPROVE_AND_CREATE_PR", "digest-1", "looks good");
    }

    @Test
    void retryStreamsEvents() {
        when(remediationWorkflow.retryPublish(sampleUser, "run-123")).thenReturn(Flux.just("retry-event"));

        Flux<String> result = controller.retry("run-123", exchange);

        List<String> events = result.collectList().block();
        assertEquals(List.of("retry-event"), events);
        verify(remediationWorkflow).retryPublish(sampleUser, "run-123");
    }
}
