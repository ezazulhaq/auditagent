package com.cb.auditagent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.ApprovalPreview;
import com.cb.auditagent.dto.RunDecisionRequest;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.RemediationWorkflowService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/runs")
public class RunPublicationController {
    private final RemediationWorkflowService remediationWorkflow;
    private final GitHubAuthService auth;

    public RunPublicationController(RemediationWorkflowService remediationWorkflow, GitHubAuthService auth) {
        this.remediationWorkflow = remediationWorkflow;
        this.auth = auth;
    }

    @GetMapping("/{runId}/approval-preview")
    public ApprovalPreview preview(@PathVariable String runId, ServerWebExchange exchange) {
        return remediationWorkflow.approvalPreview(auth.requireUser(exchange), runId);
    }

    @PostMapping(value = "/{runId}/decision", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> decision(@PathVariable String runId, @RequestBody RunDecisionRequest request,
            ServerWebExchange exchange) {
        return remediationWorkflow.decide(auth.requireUser(exchange), runId, request.getVulnId(),
                request.getDecision(), request.getApprovalDigest(), request.getReason());
    }

    @PostMapping(value = "/{runId}/retry-publish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> retry(@PathVariable String runId, ServerWebExchange exchange) {
        return remediationWorkflow.retryPublish(auth.requireUser(exchange), runId);
    }
}
