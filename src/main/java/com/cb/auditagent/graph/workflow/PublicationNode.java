package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.PublishResult;
import com.cb.auditagent.domain.RunPublicationRecord;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.PullRequestProvider;
import com.cb.auditagent.service.ReporterService;
import com.cb.auditagent.service.SourceControlProvider;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Component
public class PublicationNode implements NodeAction<WorkflowState> {

        private final DatabaseService database;
        private final GitHubApiClient github;
        private final PullRequestProvider pullRequests;
        private final SourceControlProvider sourceControl;
        private final ReporterService reporter;

        public PublicationNode(
                        DatabaseService database,
                        GitHubApiClient github,
                        PullRequestProvider pullRequests,
                        SourceControlProvider sourceControl,
                        ReporterService reporter) {
                this.database = database;
                this.github = github;
                this.pullRequests = pullRequests;
                this.sourceControl = sourceControl;
                this.reporter = reporter;
        }

        private void refreshReport(String reportKey, ManagedRepository repository) {
                database.getReport(reportKey).ifPresent(report -> {
                        List<Vulnerability> findings = new ArrayList<>(report.getFindings());
                        for (int i = 0; i < findings.size(); i++) {
                                int index = i;
                                database.getVulnerabilityById(findings.get(i).getId(), reportKey)
                                                .ifPresent(v -> findings.set(index, v));
                        }
                        String markdown = reporter.generateMarkdown(findings, report.getMetadata(),
                                        repository.fullName());
                        String html = reporter.generateHtml(findings, report.getMetadata(), repository.fullName());
                        database.saveReport(reportKey, markdown, html, findings, report.getMetadata());
                });
        }

        @Override
        public Map<String, Object> apply(WorkflowState state) throws Exception {
                String runId = state.getRunId();
                AuthenticatedUser user = state.getAuthenticatedUser();

                AgentRunRecord run = database.getAgentRun(runId)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));

                RunPublicationRecord publication = database.getRunPublication(runId)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));

                ManagedRepository repository = database.getManagedRepository(publication.repositoryId())
                                .orElseThrow(() -> new IllegalArgumentException("Repository is not managed"));

                String installationToken = github.installationToken(publication.installationId());

                Path workspace = Path.of(publication.workspacePath());

                List<String> changedFiles = sourceControl.changedFiles(workspace);
                boolean touchesWorkflows = changedFiles.stream()
                                .anyMatch(f -> f.replace('\\', '/').startsWith(".github/workflows/"));
                if (touchesWorkflows && !github.installationHasWorkflowPermission(publication.installationId())) {
                        throw new IllegalStateException("Remediation touches GitHub Actions workflow files (.github/workflows/), which requires the GitHub App to have 'Workflows: Read and write' permission. Please grant 'Workflows: Read and write' permission in your GitHub App settings (Developer settings -> GitHub Apps -> Permissions & events -> Repository permissions -> Workflows) and accept the updated permissions for this repository.");
                }

                database.updateAgentRun(
                                run.runId(),
                                AgentPhase.COMMITTING,
                                AgentRunStatus.PUBLISHING,
                                run.iteration(),
                                run.retryCount(),
                                run.patchApplied(),
                                run.compilePassed(),
                                run.rescanPassed(),
                                run.testsPassed(),
                                run.checkpointJson(),
                                run.finalSummary(),
                                null,
                                null);

                Vulnerability vulnerability = database.getVulnerabilityById(run.vulnerabilityId())
                                .orElseThrow(() -> new IllegalStateException("Run finding no longer exists"));

                Integer issueNumber = state.<Integer>value("issueNumber").orElse(null);
                String summary = "Automated security remediation for " + vulnerability.getRuleId();
                if (issueNumber != null && issueNumber > 0) {
                        summary += "\n\nFixes #" + issueNumber;
                }
                String approvingUser = user != null ? user.login()
                                : (publication.approvedBy() != null ? publication.approvedBy() : "system");
                java.time.LocalDateTime approvedAt = publication.approvedAt() != null
                                ? publication.approvedAt()
                                : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

                PublishResult result;
                try {
                        result = pullRequests.publish(repository, workspace,
                                        publication.baseBranch(), publication.branchName(), installationToken, approvingUser,
                                        run.runId(), vulnerability, summary, checkpoint -> {
                                                // Progress callback omitted for brevity in graph node
                                        });
                } catch (Exception e) {
                        String commitSha = null;
                        try {
                                commitSha = sourceControl.workspaceHead(workspace);
                        } catch (Exception ignored) {
                        }
                        AgentPhase failedPhase = commitSha == null ? AgentPhase.COMMITTING : AgentPhase.PUSHING;
                        database.updateAgentRun(run.runId(), failedPhase, AgentRunStatus.PUBLISH_FAILED,
                                        run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                                        run.rescanPassed(), run.testsPassed(), run.checkpointJson(), run.finalSummary(),
                                        "PUBLISH_FAILED", e.getMessage());
                        RunPublicationRecord failed = new RunPublicationRecord(
                                        publication.runId(), publication.userId(), publication.repositoryId(),
                                        publication.installationId(), publication.reportKey(), publication.baseBranch(),
                                        publication.baseSha(), publication.workspacePath(), publication.branchName(),
                                        publication.approvalDigest(), approvingUser, approvedAt,
                                        commitSha != null ? commitSha : publication.commitSha(), "FAILED",
                                        publication.pullRequestNumber(), publication.pullRequestUrl(), publication.pullRequestState(),
                                        e.getMessage());
                        database.saveRunPublication(failed);
                        throw e;
                }

                RunPublicationRecord completed = new RunPublicationRecord(
                                publication.runId(), publication.userId(), publication.repositoryId(),
                                publication.installationId(), publication.reportKey(), publication.baseBranch(),
                                publication.baseSha(), publication.workspacePath(), publication.branchName(),
                                publication.approvalDigest(), approvingUser, approvedAt,
                                result.commitSha(), "PUSHED", result.pullRequestNumber(), result.pullRequestUrl(),
                                "OPEN", null);
                database.saveRunPublication(completed);

                vulnerability.setStatus(VulnerabilityStatus.PR_OPEN);
                database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(),
                                vulnerability.getProposedFix());

                database.updateAgentRun(run.runId(), AgentPhase.TRACKING_PR, AgentRunStatus.PR_OPEN,
                                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                                run.rescanPassed(),
                                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);

                refreshReport(publication.reportKey(), repository);

                Map<String, Object> updates = new HashMap<>();
                updates.put("runStatus", "PUBLISHED");
                updates.put("pullRequestUrl", result.pullRequestUrl());
                updates.put("pullRequestNumber", result.pullRequestNumber());
                updates.put("workflowMessage", "Pull request created successfully: " + result.pullRequestUrl());
                return updates;
        }
}
