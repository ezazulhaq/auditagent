package com.cb.auditagent.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.dto.AnalyzeRequest;
import com.cb.auditagent.dto.ChatRequest;
import com.cb.auditagent.dto.ScanRequest;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.LlmService;
import com.cb.auditagent.service.RemediationWorkflowService;
import com.cb.auditagent.service.RepositoryAccessService;
import com.cb.auditagent.service.ScanOrchestratorService;
import com.cb.auditagent.service.SkillManagerService;

import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class AuditController {
    private final SkillManagerService skills;
    private final LlmService llm;
    private final ScanOrchestratorService scanOrchestrator;
    private final RemediationWorkflowService remediationWorkflow;
    private final RepositoryAccessService repositoryAccess;
    private final GitHubAuthService auth;
    private final DatabaseService database;

    public AuditController(SkillManagerService skills, LlmService llm,
            ScanOrchestratorService scanOrchestrator,
            RemediationWorkflowService remediationWorkflow,
            RepositoryAccessService repositoryAccess,
            GitHubAuthService auth,
            DatabaseService database) {
        this.skills = skills;
        this.llm = llm;
        this.scanOrchestrator = scanOrchestrator;
        this.remediationWorkflow = remediationWorkflow;
        this.repositoryAccess = repositoryAccess;
        this.auth = auth;
        this.database = database;
    }

    @PostMapping("/scan")
    public ResponseEntity<Flux<String>> scan(@RequestBody ScanRequest request, ServerWebExchange exchange) {
        if (request.getRepositoryId() == null || request.getBranch() == null || request.getBranch().isBlank()) {
            throw new IllegalArgumentException("repositoryId and branch are required; local paths are disabled");
        }
        Flux<String> stream = scanOrchestrator.scan(auth.requireUser(exchange), request.getThreadId(),
                request.getRepositoryId(), request.getBranch(), request.getScannerName(), request.isForceRescan());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE).body(stream);
    }

    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyze(@RequestBody AnalyzeRequest request, ServerWebExchange exchange) {
        if (request.getRepositoryId() == null || request.getBranch() == null || request.getBranch().isBlank()) {
            throw new IllegalArgumentException("repositoryId and branch are required");
        }
        return remediationWorkflow.analyze(auth.requireUser(exchange), request.getThreadId(), request.getRepositoryId(),
                request.getBranch(), request.getVulnId());
    }

    @GetMapping("/reports")
    public ResponseEntity<?> report(@RequestParam long repositoryId, @RequestParam String branch,
            ServerWebExchange exchange) {
        return ResponseEntity.ok(scanOrchestrator.report(auth.requireUser(exchange), repositoryId, branch));
    }

    @GetMapping("/reports/history")
    public ResponseEntity<?> getReportHistory(@RequestParam long repositoryId, @RequestParam String branch,
            ServerWebExchange exchange) {
        repositoryAccess.requireRepositoryAccess(auth.requireUser(exchange), repositoryId);
        return ResponseEntity.ok(database.getScanHistory(repositoryId, branch));
    }

    @DeleteMapping("/reports/history")
    public ResponseEntity<?> clearScanHistory(@RequestParam long repositoryId, @RequestParam String branch,
            ServerWebExchange exchange) {
        repositoryAccess.requireRepositoryAccess(auth.requireUser(exchange), repositoryId);
        database.deleteScanHistory(repositoryId, branch);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam long repositoryId, @RequestParam String branch,
            @RequestParam String format, ServerWebExchange exchange) {
        ReportArtifact artifact = scanOrchestrator.exportReport(
                auth.requireUser(exchange), repositoryId, branch, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(artifact.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(artifact.fileName(), StandardCharsets.UTF_8).build());
        headers.setContentLength(artifact.content().length);
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }

    @GetMapping("/skills")
    public ResponseEntity<?> getSkills() {
        return ResponseEntity.ok(skills.getAllSkills().values());
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request, ServerWebExchange exchange) {
        var user = auth.requireUser(exchange);
        if (!database.memoryThreadBelongsTo(request.getThreadId(), user.userId())) {
            return Flux.error(new SecurityException("Conversation does not belong to the authenticated user"));
        }
        if (request.getRepositoryId() == null || request.getBranch() == null) {
            return Flux.error(new IllegalArgumentException("repositoryId and branch are required"));
        }
        repositoryAccess.requireRepositoryAccess(user, request.getRepositoryId());
        String reportKey = database.findLatestScanSnapshot(request.getRepositoryId(), request.getBranch())
                .map(ScanSnapshot::reportKey)
                .orElse("github:" + request.getRepositoryId() + ":" + request.getBranch());
        var findings = database.getReport(reportKey).map(com.cb.auditagent.domain.Report::getFindings)
                .orElseGet(java.util.List::of);
        return llm.chatStream(request.getThreadId(), reportKey, request.getMessage(), findings);
    }
}
