package com.cb.auditagent.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.dto.MemoryThreadRequest;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.PullRequestLifecycleService;
import com.cb.auditagent.service.RemediationWorkflowService;
import com.cb.auditagent.service.RepositoryAccessService;

import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MemoryController {
    private final DatabaseService database;
    private final RepositoryAccessService repositoryAccess;
    private final RemediationWorkflowService remediationWorkflow;
    private final PullRequestLifecycleService pullRequestLifecycle;
    private final GitHubAuthService auth;

    public MemoryController(DatabaseService database,
            RepositoryAccessService repositoryAccess,
            RemediationWorkflowService remediationWorkflow,
            PullRequestLifecycleService pullRequestLifecycle,
            GitHubAuthService auth) {
        this.database = database;
        this.repositoryAccess = repositoryAccess;
        this.remediationWorkflow = remediationWorkflow;
        this.pullRequestLifecycle = pullRequestLifecycle;
        this.auth = auth;
    }

    @PostMapping("/memory/threads")
    public ResponseEntity<?> openThread(@RequestBody MemoryThreadRequest request, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        if (request.getRepositoryId() != null)
            repositoryAccess.requireRepositoryAccess(user, request.getRepositoryId());
        String key = request.getRepositoryId() == null ? "github:general:" + user.githubUserId()
                : "github:" + request.getRepositoryId() + ":" + requireBranch(request.getBranch());
        String threadId = database.getOrCreateMemoryThread(user.userId(), key);
        return ResponseEntity.ok(threadResponse(threadId, user));
    }

    @GetMapping("/memory/threads/{threadId}")
    public ResponseEntity<?> getThread(@PathVariable String threadId, ServerWebExchange exchange) {
        return ResponseEntity.ok(threadResponse(threadId, auth.requireUser(exchange)));
    }

    @DeleteMapping("/memory/threads/{threadId}")
    public ResponseEntity<?> forgetThread(@PathVariable String threadId, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        requireOwner(threadId, user);
        database.findRecoverableRun(threadId).ifPresent(run -> remediationWorkflow.discard(user, run.runId()));
        database.deleteMemoryThread(threadId);
        return ResponseEntity.ok(Map.of("threadId", threadId, "forgotten", true));
    }

    @DeleteMapping("/memory/repositories")
    public ResponseEntity<?> forgetRepository(@RequestParam long repositoryId, @RequestParam String branch,
            ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        repositoryAccess.requireRepositoryAccess(user, repositoryId);
        String key = "github:" + repositoryId + ":" + requireBranch(branch);
        database.deleteRepositoryMemories(key);
        return ResponseEntity.ok(Map.of("repositoryId", repositoryId, "branch", branch, "forgotten", true));
    }

    @PostMapping(value = "/runs/{runId}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resume(@PathVariable String runId, @RequestParam String threadId,
            ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        requireOwner(threadId, user);
        return remediationWorkflow.resume(user, threadId, runId);
    }

    @PostMapping("/runs/{runId}/discard")
    public ResponseEntity<?> discard(@PathVariable String runId, ServerWebExchange exchange) {
        return ResponseEntity.ok(remediationWorkflow.discard(auth.requireUser(exchange), runId));
    }

    private Map<String, Object> threadResponse(String threadId, AuthenticatedUser user) {
        requireOwner(threadId, user);
        Map<String, Object> response = new LinkedHashMap<>();
        var activeRun = database.findRecoverableRun(threadId);
        activeRun.filter(run -> run.status() == com.cb.auditagent.domain.AgentRunStatus.PR_OPEN)
                .ifPresent(run -> pullRequestLifecycle.reconcilePullRequest(user, run.runId()));
        activeRun = database.findRecoverableRun(threadId);
        response.put("threadId", threadId);
        response.put("messages", database.getMemoryMessages(threadId));
        response.put("activeRun", activeRun.orElse(null));
        response.put("publication", activeRun.flatMap(run -> database.getRunPublication(run.runId())).orElse(null));
        response.put("activeFinding",
                activeRun.flatMap(run -> database.getVulnerabilityById(run.vulnerabilityId())).orElse(null));
        response.put("ftsAvailable", database.isFtsAvailable());
        return response;
    }

    private void requireOwner(String threadId, AuthenticatedUser user) {
        if (!database.memoryThreadBelongsTo(threadId, user.userId())) {
            throw new SecurityException("Conversation does not belong to the authenticated user");
        }
    }

    private String requireBranch(String branch) {
        if (branch == null || branch.isBlank())
            throw new IllegalArgumentException("branch is required");
        return branch;
    }
}
