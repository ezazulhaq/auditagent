package com.cb.auditagent.service;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.graph.workflow.RemediationWorkflowGraph;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RemediationWorkflowService {
    private static final Logger logger = LoggerFactory.getLogger(RemediationWorkflowService.class);
    private static final int MAX_DIFF_CHARS = 200_000;

    private final DatabaseService database;
    private final SourceControlProvider sourceControl;
    private final PullRequestProvider pullRequests;
    private final GitHubApiClient github;
    private final GitHubAuthService auth;
    private final LlmService llm;
    private final ObjectMapper objectMapper;
    private final MemoryRedactor redactor;
    private final RemediationWorkflowGraph workflowGraph;
    private final RepositoryAccessService repositoryAccess;
    private final ReporterService reporter;

    public RemediationWorkflowService(
            DatabaseService database,
            SourceControlProvider sourceControl,
            PullRequestProvider pullRequests,
            GitHubApiClient github,
            GitHubAuthService auth,
            LlmService llm,
            ObjectMapper objectMapper,
            MemoryRedactor redactor,
            RemediationWorkflowGraph workflowGraph,
            RepositoryAccessService repositoryAccess,
            ReporterService reporter) {
        this.database = database;
        this.sourceControl = sourceControl;
        this.pullRequests = pullRequests;
        this.github = github;
        this.auth = auth;
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
        this.workflowGraph = workflowGraph;
        this.repositoryAccess = repositoryAccess;
        this.reporter = reporter;
    }

    public Flux<String> analyze(AuthenticatedUser user, String threadId, long repositoryId,
            String branch, String vulnerabilityId) {
        requireThreadOwner(threadId, user);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            java.util.Timer keepAliveTimer = new java.util.Timer(true);
            keepAliveTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        emit(sink, Map.of("type", "keepalive"));
                    } catch (Exception ignore) {
                    }
                }
            }, 15000, 15000);
            try {
                ManagedRepository repository = repositoryAccess.requireRepositoryAccess(user, repositoryId);
                ScanSnapshot snapshot = database.findLatestScanSnapshot(repositoryId, branch)
                        .orElseThrow(() -> new IllegalStateException("Run a scan for this repository branch first"));

                String workflowId = threadId + "-" + vulnerabilityId;
                database.deleteGraphCheckpoints(workflowId);
                java.util.Map<String, Object> state = new java.util.HashMap<>();
                state.put("authenticatedUser", user);
                state.put("threadId", threadId);
                state.put("repositoryId", repositoryId);
                state.put("branch", branch);
                state.put("vulnerabilityId", vulnerabilityId);

                var config = org.bsc.langgraph4j.RunnableConfig.builder().threadId(workflowId).build();
                var stream = workflowGraph.getGraph().stream(org.bsc.langgraph4j.GraphInput.args(state), config);

                for (var node : stream) {
                    String nodeName = node.node();
                    com.cb.auditagent.graph.workflow.WorkflowState s = node.state();

                    emit(sink, java.util.Map.of("type", "graph_step", "node", nodeName,
                            "status", "completed"));

                    if ("clone_workspace".equals(nodeName)) {
                        emit(sink, java.util.Map.of("type", "workspace_prepared", "runId", s.getRunId(),
                                "repository", repository.fullName(), "branch", branch, "baseSha", snapshot.baseSha()));
                    } else if ("approval_ready".equals(nodeName)) {
                        emit(sink, java.util.Map.of("type", "approval_ready", "runId", s.getRunId(), "preview",
                                s.getApprovalPreview()));
                        emit(sink, java.util.Map.of("type", "analysis_complete", "runId", s.getRunId(),
                                "requires_approval", true, "preview", s.getApprovalPreview(),
                                "message", s.getWorkflowMessage() != null ? s.getWorkflowMessage()
                                        : "Remediation verified. Awaiting human approval."));
                    } else if ("publish".equals(nodeName)) {
                        emit(sink, java.util.Map.of("type", "publish_progress", "runId", s.getRunId(), "phase",
                                "TRACKING_PR",
                                "message",
                                s.getWorkflowMessage() != null ? s.getWorkflowMessage() : "Pull request created."));
                        if (s.getPullRequestNumber() != null && s.getPullRequestUrl() != null) {
                            emit(sink, java.util.Map.of("type", "pr_created", "runId", s.getRunId(),
                                    "pullRequestNumber", s.getPullRequestNumber(), "pullRequestUrl",
                                    s.getPullRequestUrl()));
                        }
                        emit(sink, java.util.Map.of("type", "complete", "runId", s.getRunId(),
                                "runStatus", "PUBLISHED", "message",
                                s.getWorkflowMessage() != null ? s.getWorkflowMessage() : "Pull request created."));
                    }
                }
                sink.tryEmitComplete();
            } catch (Exception e) {
                logger.error("Managed remediation failed: {}", safeMessage(e));
                emit(sink, java.util.Map.of("type", "status", "message", "Remediation failed: " + safeMessage(e)));
                sink.tryEmitComplete();
            } finally {
                keepAliveTimer.cancel();
            }
        });
        return sink.asFlux();
    }

    public ApprovalPreview approvalPreview(AuthenticatedUser user, String runId) {
        return buildApprovalPreview(user, runId, false);
    }

    public Flux<String> decide(AuthenticatedUser user, String runId, String vulnerabilityId,
            String decision, String suppliedDigest, String reason) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            try {
                AgentRunRecord run = requireOwnedRun(user, runId);
                if (!run.vulnerabilityId().equalsIgnoreCase(vulnerabilityId))
                    throw new SecurityException("Run and vulnerability do not match");

                String workflowId = run.threadId() + "-" + run.vulnerabilityId();
                var config = org.bsc.langgraph4j.RunnableConfig.builder().threadId(workflowId).build();

                java.util.Map<String, Object> update = new java.util.HashMap<>();
                update.put("decision", decision);

                workflowGraph.getGraph().updateState(config, update);
                var stream = workflowGraph.getGraph().stream(org.bsc.langgraph4j.GraphInput.resume(), config);

                for (var node : stream) {
                    String nodeName = node.node();
                    com.cb.auditagent.graph.workflow.WorkflowState s = node.state();
                    if ("reject".equals(nodeName)) {
                        database.saveRejectedRepositoryMemory(runId, reason);
                        emit(sink, java.util.Map.of("type", "complete", "runId", runId,
                                "runStatus", "REJECTED", "message",
                                s.getWorkflowMessage() != null ? s.getWorkflowMessage()
                                        : "Fix rejected. No branch or pull request was created."));
                    } else if ("publish".equals(nodeName)) {
                        emit(sink, java.util.Map.of("type", "publish_progress", "runId", runId, "phase", "TRACKING_PR",
                                "message",
                                s.getWorkflowMessage() != null ? s.getWorkflowMessage() : "Pull request created."));
                        if (s.getPullRequestNumber() != null && s.getPullRequestUrl() != null) {
                            emit(sink, java.util.Map.of("type", "pr_created", "runId", runId,
                                    "runStatus", "PR_OPEN",
                                    "pullRequestNumber", s.getPullRequestNumber(),
                                    "pullRequestUrl", s.getPullRequestUrl(),
                                    "message",
                                    "Pull request created. The finding will be marked FIXED only after merge."));
                        }
                        emit(sink, java.util.Map.of("type", "complete", "runId", runId,
                                "runStatus", "PUBLISHED", "message",
                                s.getWorkflowMessage() != null ? s.getWorkflowMessage() : "Pull request created."));
                    }
                }
                sink.tryEmitComplete();
            } catch (Exception e) {
                emit(sink, java.util.Map.of("type", "publish_failed", "runId", runId, "runStatus", safeRunStatus(runId),
                        "message", safeMessage(e)));
                sink.tryEmitComplete();
            }
        });
        return sink.asFlux();
    }

    public Flux<String> retryPublish(AuthenticatedUser user, String runId) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            try {
                AgentRunRecord run = requireOwnedRun(user, runId);
                if (run.status() != AgentRunStatus.PUBLISH_FAILED && run.status() != AgentRunStatus.INTERRUPTED)
                    throw new IllegalStateException("Run has no recoverable publication failure");
                RunPublicationRecord publication = requirePublication(runId);
                if (publication.approvedAt() == null)
                    throw new SecurityException("Run was never approved");
                publish(user, run, publication, sink, false);
            } catch (Exception e) {
                String status = safeRunStatus(runId);
                emit(sink, Map.of("type", "publish_failed", "runId", runId, "runStatus", status,
                        "message", safeMessage(e)));
            } finally {
                sink.tryEmitComplete();
            }
        });
        return sink.asFlux();
    }

    public Flux<String> resume(AuthenticatedUser user, String threadId, String runId) {
        requireThreadOwner(threadId, user);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            try {
                AgentRunRecord run = requireOwnedRun(user, runId);
                if (!run.threadId().equals(threadId))
                    throw new SecurityException("Run belongs to another conversation");
                if (run.status() != AgentRunStatus.INTERRUPTED) {
                    throw new IllegalStateException("Only an interrupted remediation can be resumed");
                }
                RunPublicationRecord publication = requirePublication(runId);
                if (publication.approvedAt() != null) {
                    throw new IllegalStateException("This interrupted run was already approved; use Retry publishing");
                }
                Path workspace = Path.of(publication.workspacePath());
                Map<String, String> hashes = sourceControl.changedFileHashes(workspace);
                for (RunChangeRecord change : database.getRunChanges(runId)) {
                    if (!"APPLIED".equals(change.state()))
                        continue;
                    String actual = hashes.get(change.filePath().replace('\\', '/'));
                    if (actual == null || !change.afterHash().equalsIgnoreCase(actual)) {
                        conflictRun(run, "FILE_HASH_CONFLICT",
                                "Workspace changed after checkpoint: " + change.filePath());
                        throw new IllegalStateException("Resume blocked because the isolated workspace changed");
                    }
                }
                Vulnerability vulnerability = database
                        .getVulnerabilityById(run.vulnerabilityId(), publication.reportKey())
                        .orElseThrow(() -> new IllegalStateException("Run finding no longer exists"));
                LlmService.AgentResult result = llm.runAgentLoop(runId, threadId, vulnerability,
                        workspace.toString(), publication.reportKey(), true, run.iteration(), event -> {
                            event.put("runId", runId);
                            emit(sink, event);
                        });
                vulnerability.setProposedFix(result.getFinalResponse());
                vulnerability.setStatus(result.isVerified()
                        ? VulnerabilityStatus.AWAITING_APPROVAL
                        : VulnerabilityStatus.PATCH_FAILED);
                database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(),
                        vulnerability.getProposedFix());
                refreshReport(publication.reportKey(), repositoryAccess.requireRepository(publication.repositoryId()));
                if (result.isVerified()) {
                    ApprovalPreview preview = buildApprovalPreview(user, runId, true);
                    emit(sink, Map.of("type", "approval_ready", "runId", runId, "preview", preview));
                    emit(sink, Map.of("type", "analysis_complete", "runId", runId,
                            "requires_approval", true, "preview", preview,
                            "message", "Resumed remediation is verified and ready for structured review."));
                } else {
                    safeCleanup(workspace);
                    emit(sink, Map.of("type", "analysis_complete", "runId", runId,
                            "requires_approval", false, "message", result.getFinalResponse()));
                }
            } catch (Exception e) {
                emit(sink, Map.of("type", "status", "runId", runId, "message", "Resume failed: " + safeMessage(e)));
            } finally {
                sink.tryEmitComplete();
            }
        });
        return sink.asFlux();
    }

    public Map<String, Object> discard(AuthenticatedUser user, String runId) {
        AgentRunRecord run = requireOwnedRun(user, runId);
        RunPublicationRecord publication = requirePublication(runId);
        if (publication.approvedAt() != null || publication.pullRequestNumber() != null
                || "PUSHED".equals(publication.pushStatus())) {
            throw new IllegalStateException(
                    "A remote branch or pull request already exists; use Retry publishing or manage it on GitHub");
        }
        safeCleanup(Path.of(publication.workspacePath()));
        database.updateAgentRun(runId, AgentPhase.DISCARDED, AgentRunStatus.DISCARDED,
                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);
        database.getVulnerabilityById(run.vulnerabilityId()).ifPresent(vulnerability -> {
            vulnerability.setStatus(VulnerabilityStatus.DETECTED);
            database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(),
                    vulnerability.getProposedFix());
        });
        database.saveRunPublication(copyPublication(publication, publication.approvalDigest(), publication.approvedBy(),
                publication.approvedAt(), publication.commitSha(), "DISCARDED", null, null, "DISCARDED", null));
        refreshReport(publication.reportKey(), repositoryAccess.requireRepository(publication.repositoryId()));
        return Map.of("runId", runId, "status", "DISCARDED", "rolledBack", true);
    }

    private void publish(AuthenticatedUser user, AgentRunRecord run, RunPublicationRecord publication,
            Sinks.Many<String> sink, boolean firstAttempt) {
        ManagedRepository repository = repositoryAccess.requireRepository(publication.repositoryId());
        String userToken = auth.accessToken(user.userId());
        if (!sourceControl.userCanPush(userToken, repository))
            throw new SecurityException("GitHub write permission is required at approval time");
        String installationToken = github.installationToken(publication.installationId());
        if (!github.installationCanPublish(publication.installationId())) {
            throw new SecurityException("GitHub App requires Contents and Pull requests write permissions");
        }
        if (firstAttempt || !"PUSHED".equals(publication.pushStatus())) {
            String currentBase = sourceControl.branchHead(repository, publication.baseBranch(), installationToken);
            if (!publication.baseSha().equalsIgnoreCase(currentBase)) {
                conflictRun(run, "STALE_BASE", "Base branch advanced from the scanned commit");
                throw new IllegalStateException("Base branch changed after scanning; run a fresh scan");
            }
            if (firstAttempt) {
                publication = copyPublication(publication, publication.approvalDigest(), user.login(),
                        LocalDateTime.now(ZoneOffset.UTC), publication.commitSha(), "APPROVED", null, null, null, null);
                database.saveRunPublication(publication);
            }
        }
        Path workspace = Path.of(publication.workspacePath());
        List<String> pendingFiles = sourceControl.changedFiles(workspace);
        if (publication.commitSha() != null) {
            if (!publication.commitSha().equalsIgnoreCase(sourceControl.workspaceHead(workspace))
                    || !pendingFiles.isEmpty()) {
                conflictRun(run, "WORKSPACE_CHANGED", "The approved publication workspace changed");
                throw new IllegalStateException("Publication workspace changed after approval");
            }
        } else if (!pendingFiles.isEmpty()) {
            Map<String, String> currentHashes = sourceControl.changedFileHashes(workspace);
            String currentDigest = approvalDigest(run, publication, currentHashes,
                    database.getVerificationEvidence(run.runId()));
            if (!currentDigest.equals(publication.approvalDigest())) {
                conflictRun(run, "APPROVAL_STALE", "The approved file hashes changed before publication");
                throw new IllegalStateException("Approved files changed before publication");
            }
        }
        database.updateAgentRun(run.runId(), AgentPhase.COMMITTING, AgentRunStatus.PUBLISHING,
                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);
        emit(sink, Map.of("type", "publish_progress", "runId", run.runId(), "phase", "COMMITTING",
                "message", "Creating the approved bot commit..."));
        AtomicReference<RunPublicationRecord> checkpointed = new AtomicReference<>(publication);
        try {
            Vulnerability vulnerability = database.getVulnerabilityById(run.vulnerabilityId())
                    .orElseThrow(() -> new IllegalStateException("Run finding no longer exists"));
            String summary = prSummary(run.runId(), vulnerability);
            PublishResult result = pullRequests.publish(repository, workspace,
                    publication.baseBranch(), publication.branchName(), installationToken, user.login(),
                    run.runId(), vulnerability, summary, checkpoint -> {
                        RunPublicationRecord current = checkpointed.get();
                        String pushStatus = "PUSHED".equals(checkpoint.phase()) ? "PUSHED" : current.pushStatus();
                        RunPublicationRecord saved = copyPublication(current, current.approvalDigest(),
                                current.approvedBy(), current.approvedAt(), checkpoint.commitSha(), pushStatus,
                                current.pullRequestNumber(), current.pullRequestUrl(), current.pullRequestState(),
                                null);
                        database.saveRunPublication(saved);
                        checkpointed.set(saved);
                        AgentPhase phase = "COMMITTED".equals(checkpoint.phase()) ? AgentPhase.PUSHING
                                : "PUSHED".equals(checkpoint.phase()) ? AgentPhase.CREATING_PR : AgentPhase.TRACKING_PR;
                        database.updateAgentRun(run.runId(), phase, AgentRunStatus.PUBLISHING,
                                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                                run.rescanPassed(), run.testsPassed(), run.checkpointJson(), run.finalSummary(), null,
                                null);
                        emit(sink, Map.of("type", "publish_progress", "runId", run.runId(), "phase", phase.name(),
                                "message",
                                "COMMITTED".equals(checkpoint.phase())
                                        ? "Approved commit created; pushing bot branch..."
                                        : "PUSHED".equals(checkpoint.phase())
                                                ? "Bot branch pushed; creating pull request..."
                                                : "Pull request created; finalizing durable state..."));
                    });
            publication = checkpointed.get();
            RunPublicationRecord completed = copyPublication(publication, publication.approvalDigest(),
                    publication.approvedBy(), publication.approvedAt(), result.commitSha(), "PUSHED",
                    result.pullRequestNumber(), result.pullRequestUrl(), "OPEN", null);
            database.saveRunPublication(completed);
            database.updateAgentRun(run.runId(), AgentPhase.TRACKING_PR, AgentRunStatus.PR_OPEN,
                    run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                    run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);
            vulnerability.setStatus(VulnerabilityStatus.PR_OPEN);
            database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(),
                    vulnerability.getProposedFix());
            refreshReport(publication.reportKey(), repository);
            safeCleanup(Path.of(publication.workspacePath()));
            emit(sink, Map.of("type", "pr_created", "runId", run.runId(), "runStatus", "PR_OPEN",
                    "branchName", result.branchName(), "commitSha", result.commitSha(),
                    "pullRequestNumber", result.pullRequestNumber(), "pullRequestUrl", result.pullRequestUrl(),
                    "message", "Pull request created. The finding will be marked FIXED only after merge."));
        } catch (Exception e) {
            publication = checkpointed.get();
            String failedStatus = "PUSHED".equals(publication.pushStatus()) ? "PUSHED" : "FAILED";
            RunPublicationRecord failed = copyPublication(publication, publication.approvalDigest(),
                    publication.approvedBy(), publication.approvedAt(), publication.commitSha(), failedStatus,
                    publication.pullRequestNumber(), publication.pullRequestUrl(), publication.pullRequestState(),
                    safeMessage(e));
            database.saveRunPublication(failed);
            AgentPhase failedPhase = "PUSHED".equals(failedStatus) ? AgentPhase.CREATING_PR
                    : publication.commitSha() == null ? AgentPhase.COMMITTING : AgentPhase.PUSHING;
            database.updateAgentRun(run.runId(), failedPhase, AgentRunStatus.PUBLISH_FAILED,
                    run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                    run.testsPassed(), run.checkpointJson(), run.finalSummary(), "PUBLISH_FAILED", safeMessage(e));
            throw e;
        }
    }

    private ApprovalPreview buildApprovalPreview(AuthenticatedUser user, String runId, boolean persist) {
        AgentRunRecord run = requireOwnedRun(user, runId);
        if (run.status() != AgentRunStatus.AWAITING_APPROVAL)
            throw new IllegalStateException("Run is not awaiting approval");
        RunPublicationRecord publication = requirePublication(runId);
        ManagedRepository repository = repositoryAccess.requireRepository(publication.repositoryId());
        Path workspace = Path.of(publication.workspacePath());
        List<String> files = sourceControl.changedFiles(workspace);
        if (files.isEmpty()) {
            database.updateAgentRun(run.runId(), AgentPhase.FAILED, AgentRunStatus.FAILED,
                    run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                    run.testsPassed(), run.checkpointJson(), run.finalSummary(), "ZERO_DIFF",
                    "Verified run has no changed files to approve.");
            throw new IllegalStateException("Verified run has no changed files to approve. Run marked as FAILED.");
        }
        String diff = sourceControl.workspaceDiff(workspace);
        if (diff.length() > MAX_DIFF_CHARS)
            diff = diff.substring(0, MAX_DIFF_CHARS) + "\n... [diff truncated]";
        Map<String, String> verification = database.getVerificationEvidence(runId);
        Map<String, String> hashes = sourceControl.changedFileHashes(workspace);
        String digest = approvalDigest(run, publication, hashes, verification);
        if (persist)
            database.saveRunPublication(copyPublication(publication, digest, null, null,
                    null, publication.pushStatus(), null, null, null, null));
        return new ApprovalPreview(runId, run.vulnerabilityId(), repository.fullName(), publication.baseBranch(),
                publication.baseSha(), publication.branchName(), files, diff, verification,
                Optional.ofNullable(run.finalSummary()).orElse("Verified security remediation"), digest);
    }

    private AgentRunRecord requireOwnedRun(AuthenticatedUser user, String runId) {
        RunPublicationRecord publication = requirePublication(runId);
        if (!publication.userId().equals(user.userId()))
            throw new SecurityException("Only the run owner may approve it");
        return database.getAgentRun(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run"));
    }

    private RunPublicationRecord requirePublication(String runId) {
        return database.getRunPublication(runId).orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));
    }

    private void requireThreadOwner(String threadId, AuthenticatedUser user) {
        if (threadId == null || !database.memoryThreadBelongsTo(threadId, user.userId()))
            throw new SecurityException("Conversation does not belong to the authenticated user");
    }

    private String approvalDigest(AgentRunRecord run, RunPublicationRecord publication,
            Map<String, String> hashes, Map<String, String> verification) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("runId", run.runId());
            canonical.put("vulnerabilityId", run.vulnerabilityId());
            canonical.put("repositoryId", publication.repositoryId());
            canonical.put("baseBranch", publication.baseBranch());
            canonical.put("baseSha", publication.baseSha());
            canonical.put("branchName", publication.branchName());
            canonical.put("files", new TreeMap<>(hashes));
            canonical.put("verification", new TreeMap<>(verification));
            return sha256(objectMapper.writeValueAsString(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("Could not bind approval preview", e);
        }
    }

    private String prSummary(String runId, Vulnerability vulnerability) {
        StringBuilder summary = new StringBuilder(Optional.ofNullable(vulnerability.getProposedFix())
                .orElse("Verified remediation for " + vulnerability.getVulnType()));
        Map<String, String> evidence = database.getVerificationEvidence(runId);
        if (!evidence.isEmpty()) {
            summary.append("\n\n## Verification\n");
            evidence.forEach(
                    (kind, result) -> summary.append("- ").append(kind).append(": ").append(result).append("\n"));
        }
        return redactor.redactAndCap(summary.toString(), 12_000);
    }

    private void refreshReport(String reportKey, ManagedRepository repository) {
        database.getReport(reportKey).ifPresent(report -> {
            List<Vulnerability> findings = new ArrayList<>(report.getFindings());
            for (int i = 0; i < findings.size(); i++) {
                int index = i;
                database.getVulnerabilityById(findings.get(i).getId(), reportKey)
                        .ifPresent(v -> findings.set(index, v));
            }
            String markdown = reporter.generateMarkdown(findings, report.getMetadata(), repository.fullName());
            String html = reporter.generateHtml(findings, report.getMetadata(), repository.fullName());
            database.saveReport(reportKey, markdown, html, findings, report.getMetadata());
        });
    }

    private RunPublicationRecord copyPublication(RunPublicationRecord p, String digest, String approvedBy,
            LocalDateTime approvedAt, String commitSha, String pushStatus,
            Integer prNumber, String prUrl, String prState, String error) {
        return new RunPublicationRecord(p.runId(), p.userId(), p.repositoryId(), p.installationId(), p.reportKey(),
                p.baseBranch(), p.baseSha(), p.workspacePath(), p.branchName(), digest, approvedBy, approvedAt,
                commitSha, pushStatus, prNumber, prUrl, prState, error);
    }

    private void conflictRun(AgentRunRecord run, String code, String detail) {
        database.updateAgentRun(run.runId(), AgentPhase.CONFLICTED, AgentRunStatus.CONFLICTED, run.iteration(),
                run.retryCount(),
                run.patchApplied(), run.compilePassed(), run.rescanPassed(), run.testsPassed(), run.checkpointJson(),
                run.finalSummary(), code, detail);
    }

    private void emit(Sinks.Many<String> sink, Map<String, ?> event) {
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            sink.tryEmitNext("{\"type\":\"status\",\"message\":\"Event serialization failed\"}");
        }
    }

    private void safeCleanup(Path path) {
        try {
            sourceControl.cleanupWorkspace(path);
        } catch (Exception e) {
            logger.warn("Managed workspace cleanup failed: {}", safeMessage(e));
        }
    }

    private String safeMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null &&
                (cause instanceof java.util.concurrent.ExecutionException ||
                        cause instanceof java.lang.reflect.InvocationTargetException ||
                        cause instanceof RuntimeException)) {
            if (cause.getCause() == cause)
                break;
            cause = cause.getCause();
        }
        String message = Optional.ofNullable(cause.getMessage()).orElse(cause.getClass().getSimpleName());
        if (message.contains("STALE_BASE")) {
            return "The repository branch has advanced since the last scan (e.g., a PR was merged). Please run a fresh scan before analyzing further findings.";
        }
        return redactor.redactAndCap(message, 500);
    }

    private String safeRunStatus(String runId) {
        try {
            return database.getAgentRun(runId).map(value -> value.status().name()).orElse("PUBLISH_FAILED");
        } catch (Exception ignored) {
            return "PUBLISH_FAILED";
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    public void cleanupStaleWorkspaces() {
        try {
            List<RunPublicationRecord> stale = database.findStaleWorkspaces(
                    List.of("FAILED", "INTERRUPTED", "PR_MERGED", "PR_CLOSED", "REJECTED", "DISCARDED"),
                    LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
            int cleaned = 0;
            for (RunPublicationRecord pub : stale) {
                try {
                    sourceControl.cleanupWorkspace(Path.of(pub.workspacePath()));
                    cleaned++;
                } catch (Exception e) {
                    logger.debug("Workspace already cleaned or inaccessible: {}", pub.workspacePath());
                }
            }
            if (cleaned > 0)
                logger.info("Cleaned {} stale workspaces", cleaned);
        } catch (Exception e) {
            logger.warn("Stale workspace cleanup failed: {}", safeMessage(e));
        }
    }
}
