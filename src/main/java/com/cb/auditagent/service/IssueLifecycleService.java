package com.cb.auditagent.service;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.domain.TrackedIssueRecord;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class IssueLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(IssueLifecycleService.class);
    private static final Set<String> TRIGGER_LABELS = Set.of("auditagent-fix", "security", "auditagent");
    private static final Pattern VULN_ID_PATTERN = Pattern.compile("VULN-[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);

    private final DatabaseService database;
    private final GitHubApiClient github;
    private final RemediationWorkflowService remediationWorkflow;
    private final LlmService llm;
    private final ObjectMapper objectMapper;

    public IssueLifecycleService(
            DatabaseService database,
            GitHubApiClient github,
            RemediationWorkflowService remediationWorkflow,
            LlmService llm,
            ObjectMapper objectMapper) {
        this.database = database;
        this.github = github;
        this.remediationWorkflow = remediationWorkflow;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    public void handleIssueEvent(long repositoryId, int issueNumber, String action, String title, String body,
            List<String> labels) {
        ManagedRepository repository = database.getManagedRepository(repositoryId).orElse(null);
        if (repository == null) {
            logger.info("Ignoring issue event for unmanaged repository ID {}", repositoryId);
            return;
        }

        String threadId = "issue-" + repositoryId + "-" + issueNumber;

        if ("closed".equalsIgnoreCase(action)) {
            handleIssueClosed(repositoryId, threadId, issueNumber);
            return;
        }

        if (!"opened".equalsIgnoreCase(action) && !"reopened".equalsIgnoreCase(action)
                && !"labeled".equalsIgnoreCase(action)) {
            logger.debug("Ignoring issue action '{}' for issue #{}", action, issueNumber);
            return;
        }

        if (!isTriggered(labels, title, body)) {
            logger.debug("Issue #{} does not match trigger criteria (labels or mentions)", issueNumber);
            return;
        }

        String id = repositoryId + "-" + issueNumber;
        Optional<TrackedIssueRecord> existing = database.findTrackedIssue(repositoryId, issueNumber);
        TrackedIssueRecord initial = new TrackedIssueRecord(
                id,
                repositoryId,
                issueNumber,
                title != null && !title.isBlank() ? title : (existing.isPresent() ? existing.get().title() : "Issue #" + issueNumber),
                body != null ? body : (existing.isPresent() ? existing.get().body() : null),
                labels != null ? labels : (existing.isPresent() ? existing.get().labels() : List.of()),
                "OPEN",
                existing.map(TrackedIssueRecord::targetVulnerabilityId).orElse(null),
                existing.map(TrackedIssueRecord::runId).orElse(null),
                "ANALYZING",
                existing.map(TrackedIssueRecord::pullRequestNumber).orElse(null),
                existing.map(TrackedIssueRecord::pullRequestUrl).orElse(null),
                "Issue received, analyzing context and matching findings...",
                existing.map(TrackedIssueRecord::createdAt).orElse(LocalDateTime.now()),
                LocalDateTime.now()
        );
        database.saveTrackedIssue(initial);

        Thread.startVirtualThread(() -> processIssueRemediation(repository, issueNumber, threadId, title, body));
    }

    public void syncIssuesFromGitHub(ManagedRepository repository, String token) {
        if (repository == null || token == null || token.isBlank()) return;
        try {
            List<GitHubApiClient.GitHubIssueInfo> issues = github.listRepositoryIssues(repository, token);
            for (GitHubApiClient.GitHubIssueInfo issue : issues) {
                if (!isTriggered(issue.labels(), issue.title(), issue.body())) {
                    continue;
                }
                Optional<TrackedIssueRecord> existing = database.findTrackedIssue(repository.repositoryId(), issue.number());
                boolean isOpen = "OPEN".equalsIgnoreCase(issue.state());
                if (existing.isEmpty()) {
                    String id = repository.repositoryId() + "-" + issue.number();
                    TrackedIssueRecord record = new TrackedIssueRecord(
                            id,
                            repository.repositoryId(),
                            issue.number(),
                            issue.title() != null && !issue.title().isBlank() ? issue.title() : "Issue #" + issue.number(),
                            issue.body(),
                            issue.labels(),
                            isOpen ? "OPEN" : "CLOSED",
                            null,
                            null,
                            isOpen ? "ANALYZING" : "DISCARDED",
                            null,
                            null,
                            isOpen ? "Synced from GitHub, analyzing context..." : "Issue closed on GitHub",
                            LocalDateTime.now(),
                            LocalDateTime.now()
                    );
                    database.saveTrackedIssue(record);
                    if (isOpen) {
                        String threadId = "issue-" + repository.repositoryId() + "-" + issue.number();
                        Thread.startVirtualThread(() -> processIssueRemediation(repository, issue.number(), threadId, issue.title(), issue.body()));
                    }
                } else {
                    TrackedIssueRecord cur = existing.get();
                    if (!isOpen && !"CLOSED".equalsIgnoreCase(cur.state())) {
                        TrackedIssueRecord updated = new TrackedIssueRecord(
                                cur.id(), cur.repositoryId(), cur.issueNumber(), issue.title(), issue.body(),
                                issue.labels(), "CLOSED", cur.targetVulnerabilityId(), cur.runId(),
                                "DISCARDED", cur.pullRequestNumber(), cur.pullRequestUrl(),
                                "Issue closed on GitHub", cur.createdAt(), LocalDateTime.now()
                        );
                        database.saveTrackedIssue(updated);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not sync issues from GitHub for {}: {}", repository.fullName(), e.getMessage());
        }
    }

    private boolean isTriggered(List<String> labels, String title, String body) {
        if (labels != null) {
            for (String label : labels) {
                if (label != null && TRIGGER_LABELS.contains(label.trim().toLowerCase())) {
                    return true;
                }
            }
        }
        String combined = ((title != null ? title : "") + " " + (body != null ? body : "")).toLowerCase();
        if (combined.contains("@auditagent")) {
            return true;
        }
        return VULN_ID_PATTERN.matcher(combined).find();
    }

    private void handleIssueClosed(long repositoryId, String threadId, int issueNumber) {
        database.findTrackedIssue(repositoryId, issueNumber).ifPresent(tracked -> {
            TrackedIssueRecord updated = new TrackedIssueRecord(
                    tracked.id(),
                    tracked.repositoryId(),
                    tracked.issueNumber(),
                    tracked.title(),
                    tracked.body(),
                    tracked.labels(),
                    "CLOSED",
                    tracked.targetVulnerabilityId(),
                    tracked.runId(),
                    "DISCARDED",
                    tracked.pullRequestNumber(),
                    tracked.pullRequestUrl(),
                    "Issue closed on GitHub",
                    tracked.createdAt(),
                    LocalDateTime.now()
            );
            database.saveTrackedIssue(updated);
        });

        database.findRecoverableRuns(threadId).forEach(run -> {
            if (run.status() == AgentRunStatus.AWAITING_APPROVAL || run.status() == AgentRunStatus.ACTIVE
                    || run.status() == AgentRunStatus.INTERRUPTED) {
                logger.info("Discarding run {} because issue #{} was closed", run.runId(), issueNumber);
                database.updateAgentRun(run.runId(), AgentPhase.DISCARDED, AgentRunStatus.DISCARDED,
                        run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                        run.rescanPassed(), run.testsPassed(), run.checkpointJson(),
                        "Run discarded because GitHub issue #" + issueNumber + " was closed", null, null);
            }
        });
    }

    private void updateTrackedIssueStatus(long repositoryId, int issueNumber, String status, String vulnId,
            String runId, Integer prNumber, String prUrl, String message) {
        database.findTrackedIssue(repositoryId, issueNumber).ifPresent(existing -> {
            TrackedIssueRecord updated = new TrackedIssueRecord(
                    existing.id(),
                    existing.repositoryId(),
                    existing.issueNumber(),
                    existing.title(),
                    existing.body(),
                    existing.labels(),
                    existing.state(),
                    vulnId != null ? vulnId : existing.targetVulnerabilityId(),
                    runId != null ? runId : existing.runId(),
                    status != null ? status : existing.remediationStatus(),
                    prNumber != null ? prNumber : existing.pullRequestNumber(),
                    prUrl != null ? prUrl : existing.pullRequestUrl(),
                    message != null ? message : existing.statusMessage(),
                    existing.createdAt(),
                    LocalDateTime.now()
            );
            database.saveTrackedIssue(updated);
        });
    }

    private void processIssueRemediation(ManagedRepository repository, int issueNumber, String threadId,
            String title, String body) {
        try {
            String branch = repository.defaultBranch() != null && !repository.defaultBranch().isBlank()
                    ? repository.defaultBranch()
                    : "master";
            String repoPath = "github:" + repository.repositoryId() + ":" + branch;
            database.ensureMemoryThread(threadId, "github-bot", repoPath);

            Optional<AgentRunRecord> existingRun = database.findRecoverableRun(threadId);
            if (existingRun.isPresent() && (existingRun.get().status() == AgentRunStatus.PR_OPEN
                    || existingRun.get().status() == AgentRunStatus.PUBLISHING
                    || existingRun.get().status() == AgentRunStatus.AWAITING_APPROVAL
                    || existingRun.get().status() == AgentRunStatus.ACTIVE)) {
                logger.info("Issue #{} already has an active or open PR run: {}", issueNumber,
                        existingRun.get().runId());
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber,
                        existingRun.get().status().name(), null, existingRun.get().runId(), null, null,
                        "Issue already has an active or open PR run (" + existingRun.get().runId() + ")");
                return;
            }

            Optional<ScanSnapshot> snapshotOpt = database.findLatestScanSnapshot(repository.repositoryId(), branch);
            if (snapshotOpt.isEmpty()) {
                String msg = "no scan report exists for branch `" + branch + "`. Please run a scan on `" + branch + "` first.";
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "FAILED", null, null, null, null, msg);
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but " + msg);
                return;
            }

            Optional<Report> reportOpt = database.getReport(snapshotOpt.get().reportKey());
            if (reportOpt.isEmpty() || reportOpt.get().getFindings() == null || reportOpt.get().getFindings().isEmpty()) {
                String msg = "no active detected vulnerabilities were found on branch `" + branch + "`. If this is a new vulnerability, please trigger a scan or supply the finding ID.";
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "FAILED", null, null, null, null, msg);
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but " + msg);
                return;
            }

            List<Vulnerability> activeFindings = reportOpt.get().getFindings().stream()
                    .filter(v -> v.getStatus() == VulnerabilityStatus.DETECTED
                            || v.getStatus() == VulnerabilityStatus.PATCH_FAILED)
                    .toList();

            if (activeFindings.isEmpty()) {
                String msg = "all findings on branch `" + branch + "` have already been addressed or resolved.";
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "FIXED", null, null, null, null, msg);
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but " + msg);
                return;
            }

            Optional<String> matchedVulnId = llm.matchFindingForIssue(title, body, activeFindings);
            if (matchedVulnId.isEmpty()) {
                String msg = "Could not identify a matching vulnerability from active findings on branch `" + branch + "`.";
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "UNMATCHED", null, null, null, null, msg);
                postComment(repository, issueNumber,
                        "AuditAgent analyzed this issue against active findings in branch `" + branch
                                + "`, but could not identify a matching vulnerability. Please specify the vulnerability ID (e.g., `VULN-XXXXXX`) in the issue title or body.");
                return;
            }

            String vulnId = matchedVulnId.get();
            updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "ANALYZING", vulnId, null, null, null,
                    "Matched finding " + vulnId + ". Starting autonomous remediation...");
            logger.info("Starting autonomous remediation for issue #{} (finding {}) in {}", issueNumber, vulnId,
                    repository.fullName());

            AuthenticatedUser botUser = new AuthenticatedUser("github-bot", 0L, "github-bot", "GitHub Bot", null,
                    "internal");

            AtomicReference<String> runIdRef = new AtomicReference<>();
            AtomicReference<String> digestRef = new AtomicReference<>();

            remediationWorkflow.analyze(botUser, threadId, repository.repositoryId(), branch, vulnId, issueNumber)
                    .doOnNext(json -> {
                        try {
                            JsonNode node = objectMapper.readTree(json);
                            if ("approval_ready".equals(node.path("type").asText())) {
                                runIdRef.set(node.path("runId").asText());
                                digestRef.set(node.path("preview").path("digest").asText());
                                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "AWAITING_APPROVAL",
                                        vulnId, runIdRef.get(), null, null, "Fix verified. Creating pull request...");
                            }
                        } catch (Exception ignore) {
                        }
                    })
                    .blockLast();

            if (runIdRef.get() == null || digestRef.get() == null) {
                postFailureComment(repository, issueNumber, vulnId, runIdRef.get());
                return;
            }

            AtomicInteger prNumberRef = new AtomicInteger(0);
            AtomicReference<String> prUrlRef = new AtomicReference<>();

            remediationWorkflow.decide(botUser, runIdRef.get(), vulnId, "APPROVE_AND_CREATE_PR", digestRef.get(), null)
                    .doOnNext(json -> {
                        try {
                            JsonNode node = objectMapper.readTree(json);
                            if ("pr_created".equals(node.path("type").asText())) {
                                prNumberRef.set(node.path("pullRequestNumber").asInt());
                                prUrlRef.set(node.path("pullRequestUrl").asText());
                            }
                        } catch (Exception ignore) {
                        }
                    })
                    .blockLast();

            if (prNumberRef.get() > 0 && prUrlRef.get() != null) {
                updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "PR_OPEN", vulnId, runIdRef.get(),
                        prNumberRef.get(), prUrlRef.get(), "Pull request #" + prNumberRef.get() + " opened");
                postComment(repository, issueNumber,
                        "AuditAgent has resolved this issue in pull request #" + prNumberRef.get() + " ("
                                + prUrlRef.get() + ").\n\n"
                                + "- Finding: `" + vulnId + "`\n"
                                + "- Status: Verification passed (compile, unit tests, and security rescan clean).\n\n"
                                + "Merging the pull request will automatically close this issue.");
            } else {
                postFailureComment(repository, issueNumber, vulnId, runIdRef.get());
            }

        } catch (Exception e) {
            logger.error("Failed processing issue remediation for #{} in {}: {}", issueNumber, repository.fullName(),
                    e.getMessage(), e);
            updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "FAILED", null, null, null, null,
                    "Remediation error: " + e.getMessage());
            postComment(repository, issueNumber,
                    "AuditAgent encountered an error while attempting to process this issue: " + e.getMessage());
        }
    }

    private void postFailureComment(ManagedRepository repository, int issueNumber, String vulnId, String runId) {
        String reason = "Verification failed or no valid patch could be generated";
        if (runId != null) {
            Optional<AgentRunRecord> run = database.getAgentRun(runId);
            if (run.isPresent()) {
                String detail = run.get().finalSummary() != null && !run.get().finalSummary().isBlank()
                        ? run.get().finalSummary()
                        : run.get().errorDetail();
                if (detail != null && !detail.isBlank()) {
                    reason = detail;
                }
            }
        }
        updateTrackedIssueStatus(repository.repositoryId(), issueNumber, "FAILED", vulnId, runId, null, null, reason);
        postComment(repository, issueNumber,
                "AuditAgent attempted to remediate finding `" + vulnId + "` for this issue, but automated remediation failed: "
                        + reason + ". Manual inspection is required.");
    }

    private void postComment(ManagedRepository repository, int issueNumber, String comment) {
        try {
            String token = github.installationToken(repository.installationId());
            github.createIssueComment(repository, issueNumber, comment, token);
        } catch (Exception e) {
            logger.warn("Failed to post comment to issue #{} in {}: {}", issueNumber, repository.fullName(),
                    e.getMessage());
        }
    }
}
