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
        private final ReporterService reporter;

        public PublicationNode(
                        DatabaseService database,
                        GitHubApiClient github,
                        PullRequestProvider pullRequests,
                        ReporterService reporter) {
                this.database = database;
                this.github = github;
                this.pullRequests = pullRequests;
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

                String summary = "Automated security remediation for " + vulnerability.getRuleId();

                PublishResult result = pullRequests.publish(repository, workspace,
                                publication.baseBranch(), publication.branchName(), installationToken, user.login(),
                                run.runId(), vulnerability, summary, checkpoint -> {
                                        // Progress callback omitted for brevity in graph node
                                });

                RunPublicationRecord completed = new RunPublicationRecord(
                                publication.runId(), publication.userId(), publication.repositoryId(),
                                publication.installationId(), publication.reportKey(), publication.baseBranch(),
                                publication.baseSha(), publication.workspacePath(), publication.branchName(),
                                publication.approvalDigest(), publication.approvedBy(), publication.approvedAt(),
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
