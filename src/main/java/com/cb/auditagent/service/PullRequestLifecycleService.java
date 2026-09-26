package com.cb.auditagent.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PullRequestLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestLifecycleService.class);

    private final DatabaseService database;
    private final GitHubApiClient github;
    private final PullRequestProvider pullRequests;
    private final LlmService llm;
    private final ReporterService reporter;
    private final RepositoryAccessService repositoryAccess;
    private final MeterRegistry meterRegistry;

    public PullRequestLifecycleService(
            DatabaseService database,
            GitHubApiClient github,
            PullRequestProvider pullRequests,
            LlmService llm,
            ReporterService reporter,
            RepositoryAccessService repositoryAccess,
            MeterRegistry meterRegistry) {
        this.database = database;
        this.github = github;
        this.pullRequests = pullRequests;
        this.llm = llm;
        this.reporter = reporter;
        this.repositoryAccess = repositoryAccess;
        this.meterRegistry = meterRegistry;
    }

    public void handlePullRequestEvent(long repositoryId, int prNumber, boolean merged, boolean closed) {
        RunPublicationRecord publication = database.findRunPublication(repositoryId, prNumber).orElse(null);
        if (publication == null || (!closed && !merged))
            return;

        if ("MERGED".equals(publication.pullRequestState()) || "CLOSED".equals(publication.pullRequestState())) {
            logger.info("PR event already processed for run {}", publication.runId());
            return;
        }

        AgentRunRecord run = database.getAgentRun(publication.runId()).orElse(null);
        if (run == null)
            return;
        Vulnerability vulnerability = database.getVulnerabilityById(run.vulnerabilityId()).orElse(null);
        if (vulnerability == null)
            return;
        ManagedRepository repository = database.getManagedRepository(repositoryId).orElse(null);
        if (repository == null)
            return;

        if (merged) {
            final long instId = repository.installationId();
            Thread.startVirtualThread(() -> {
                try {
                    String token = github.installationToken(instId);
                    String diff = github.getPullRequestDiff(repository, prNumber, token);
                    if (diff != null && !diff.isBlank()) {
                        llm.summarizeDiffForGlobalPattern(vulnerability, diff, run.runId(), run.threadId());
                    }
                } catch (Exception e) {
                    logger.warn("PR diff learning failed for run {}", run.runId(), e);
                }
            });
        }

        RunPublicationRecord updated = copyPublication(publication, publication.approvalDigest(),
                publication.approvedBy(),
                publication.approvedAt(), publication.commitSha(), publication.pushStatus(),
                publication.pullRequestNumber(),
                publication.pullRequestUrl(), merged ? "MERGED" : "CLOSED", null);

        Report report = database.getReport(publication.reportKey()).orElse(null);

        database.withTransaction(conn -> {
            try {
                if (merged) {
                    vulnerability.setStatus(VulnerabilityStatus.FIXED);
                    if (meterRegistry != null)
                        meterRegistry.counter("findings.resolved", "type", vulnerability.getVulnType()).increment();
                    database.updateVulnerabilityStatusTx(conn, vulnerability.getId(), vulnerability.getStatus(),
                            vulnerability.getProposedFix());
                    database.updateAgentRunTx(conn, run.runId(), AgentPhase.COMPLETE, AgentRunStatus.PR_MERGED,
                            run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                            run.rescanPassed(),
                            run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);
                    database.saveApprovedRepositoryMemoryTx(conn, publication.reportKey(), vulnerability, run.runId(),
                            run.finalSummary());

                    if (report != null) {
                        report.getFindings().stream()
                                .filter(v -> v.getFilePath().equals(vulnerability.getFilePath()))
                                .filter(v -> !v.getId().equals(vulnerability.getId()))
                                .filter(v -> v.getStatus() == VulnerabilityStatus.DETECTED
                                        || v.getStatus() == VulnerabilityStatus.PATCH_FAILED
                                        || v.getStatus() == VulnerabilityStatus.AWAITING_APPROVAL)
                                .forEach(v -> {
                                    v.setStatus(VulnerabilityStatus.FIXED);
                                    if (meterRegistry != null)
                                        meterRegistry.counter("findings.resolved", "type", v.getVulnType()).increment();
                                    try {
                                        database.updateVulnerabilityStatusTx(conn, v.getId(), v.getStatus(),
                                                v.getProposedFix());
                                    } catch (java.sql.SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                } else {
                    vulnerability.setStatus(VulnerabilityStatus.DETECTED);
                    database.updateVulnerabilityStatusTx(conn, vulnerability.getId(), vulnerability.getStatus(),
                            vulnerability.getProposedFix());
                    database.updateAgentRunTx(conn, run.runId(), AgentPhase.COMPLETE, AgentRunStatus.PR_CLOSED,
                            run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                            run.rescanPassed(),
                            run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);

                    if (report != null) {
                        report.getFindings().stream()
                                .filter(v -> v.getFilePath().equals(vulnerability.getFilePath()))
                                .filter(v -> !v.getId().equals(vulnerability.getId()))
                                .filter(v -> v.getStatus() == VulnerabilityStatus.AWAITING_APPROVAL)
                                .forEach(v -> {
                                    v.setStatus(VulnerabilityStatus.DETECTED);
                                    try {
                                        database.updateVulnerabilityStatusTx(conn, v.getId(), v.getStatus(),
                                                v.getProposedFix());
                                    } catch (java.sql.SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                    database.lowerRepositoryMemoryConfidenceTx(conn, publication.reportKey(), vulnerability,
                            run.runId());
                }
                database.saveRunPublicationTx(conn, updated);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Database error in transaction", e);
            }
        });

        refreshReport(publication.reportKey(), repository);
    }

    public void reconcilePullRequest(AuthenticatedUser user, String runId) {
        try {
            AgentRunRecord run = requireOwnedRun(user, runId);
            if (run.status() != AgentRunStatus.PR_OPEN)
                return;
            RunPublicationRecord publication = requirePublication(runId);
            if (publication.pullRequestNumber() == null)
                return;
            repositoryAccess.requireRepositoryAccess(user, publication.repositoryId());
            ManagedRepository repository = repositoryAccess.requireRepository(publication.repositoryId());
            PullRequestState state = pullRequests.getState(repository, publication.pullRequestNumber(),
                    github.installationToken(publication.installationId()));
            if (state.merged())
                handlePullRequestEvent(publication.repositoryId(), state.number(), true, true);
            else if ("closed".equalsIgnoreCase(state.state())) {
                handlePullRequestEvent(publication.repositoryId(), state.number(), false, true);
            }
        } catch (Exception e) {
            logger.warn("Could not reconcile pull request for run {}: {}", runId, e.getMessage());
        }
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

    private AgentRunRecord requireOwnedRun(AuthenticatedUser user, String runId) {
        RunPublicationRecord publication = requirePublication(runId);
        if (!publication.userId().equals(user.userId()))
            throw new SecurityException("Only the run owner may approve it");
        return database.getAgentRun(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run"));
    }

    private RunPublicationRecord requirePublication(String runId) {
        return database.getRunPublication(runId).orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));
    }
}
