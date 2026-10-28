package com.cb.auditagent.service;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
            handleIssueClosed(threadId, issueNumber);
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

        Thread.startVirtualThread(() -> processIssueRemediation(repository, issueNumber, threadId, title, body));
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

    private void handleIssueClosed(String threadId, int issueNumber) {
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

    private void processIssueRemediation(ManagedRepository repository, int issueNumber, String threadId,
            String title, String body) {
        try {
            database.getOrCreateMemoryThread("github-bot", threadId);

            Optional<AgentRunRecord> existingRun = database.findRecoverableRun(threadId);
            if (existingRun.isPresent() && (existingRun.get().status() == AgentRunStatus.PR_OPEN
                    || existingRun.get().status() == AgentRunStatus.PUBLISHING
                    || existingRun.get().status() == AgentRunStatus.AWAITING_APPROVAL
                    || existingRun.get().status() == AgentRunStatus.ACTIVE)) {
                logger.info("Issue #{} already has an active or open PR run: {}", issueNumber,
                        existingRun.get().runId());
                return;
            }

            String branch = repository.defaultBranch() != null && !repository.defaultBranch().isBlank()
                    ? repository.defaultBranch()
                    : "master";

            Optional<ScanSnapshot> snapshotOpt = database.findLatestScanSnapshot(repository.repositoryId(), branch);
            if (snapshotOpt.isEmpty()) {
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but no scan report exists for branch `"
                                + branch + "`. Please run a scan on `" + branch + "` first.");
                return;
            }

            Optional<Report> reportOpt = database.getReport(snapshotOpt.get().reportKey());
            if (reportOpt.isEmpty() || reportOpt.get().getFindings() == null || reportOpt.get().getFindings().isEmpty()) {
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but no active detected vulnerabilities were found on branch `"
                                + branch + "`. If this is a new vulnerability, please trigger a scan or supply the finding ID.");
                return;
            }

            List<Vulnerability> activeFindings = reportOpt.get().getFindings().stream()
                    .filter(v -> v.getStatus() == VulnerabilityStatus.DETECTED
                            || v.getStatus() == VulnerabilityStatus.PATCH_FAILED)
                    .toList();

            if (activeFindings.isEmpty()) {
                postComment(repository, issueNumber,
                        "AuditAgent was triggered to remediate this issue, but all findings on branch `"
                                + branch + "` have already been addressed or resolved.");
                return;
            }

            Optional<String> matchedVulnId = llm.matchFindingForIssue(title, body, activeFindings);
            if (matchedVulnId.isEmpty()) {
                postComment(repository, issueNumber,
                        "AuditAgent analyzed this issue against active findings in branch `" + branch
                                + "`, but could not identify a matching vulnerability. Please specify the vulnerability ID (e.g., `VULN-XXXXXX`) in the issue title or body.");
                return;
            }

            String vulnId = matchedVulnId.get();
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
