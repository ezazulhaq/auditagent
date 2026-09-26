package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.FindingFingerprint;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.SourceControlProvider;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CloneWorkspaceNode implements NodeAction<WorkflowState> {

    private final DatabaseService database;
    private final SourceControlProvider sourceControl;
    private final GitHubApiClient github;
    private final AgentConfig agentConfig;

    public CloneWorkspaceNode(
            DatabaseService database,
            SourceControlProvider sourceControl,
            GitHubApiClient github,
            AgentConfig agentConfig) {
        this.database = database;
        this.sourceControl = sourceControl;
        this.github = github;
        this.agentConfig = agentConfig;
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) throws Exception {
        long repositoryId = state.getRepositoryId();
        String branch = state.getBranch();
        String vulnerabilityId = state.getVulnerabilityId();

        ManagedRepository repository = database.getManagedRepository(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        ScanSnapshot snapshot = database.findLatestScanSnapshot(repositoryId, branch)
                .orElseThrow(() -> new IllegalStateException("Run a scan for this repository branch first"));

        Vulnerability vulnerability = database.getVulnerabilityById(vulnerabilityId, snapshot.reportKey())
                .orElseThrow(() -> new IllegalArgumentException("Finding does not belong to the selected scan"));

        String fingerprint = vulnerability.getFindingFingerprint();
        if (fingerprint == null) {
            fingerprint = FindingFingerprint.create(vulnerability.getRuleId(),
                    vulnerability.getFilePath(), vulnerability.getCodeSnippet());
        }

        String workspaceKey = "run-" + UUID.randomUUID();
        Path workspace = sourceControl.workspacePath(workspaceKey);

        com.cb.auditagent.domain.AgentRunRecord run;
        synchronized (database) {
            java.util.List<String> activeRuns = database.getActiveManagedRunIds(repositoryId, branch, fingerprint);
            for (String activeRunId : activeRuns) {
                database.getRunPublication(activeRunId).ifPresent(pub -> {
                    try {
                        sourceControl.cleanupWorkspace(java.nio.file.Path.of(pub.workspacePath()));
                    } catch (Exception ignored) {
                    }
                    database.saveRunPublication(new com.cb.auditagent.domain.RunPublicationRecord(
                            pub.runId(), pub.userId(), pub.repositoryId(), pub.installationId(), pub.reportKey(),
                            pub.baseBranch(), pub.baseSha(), pub.workspacePath(), pub.branchName(),
                            pub.approvalDigest(),
                            pub.approvedBy(), pub.approvedAt(), pub.commitSha(), "DISCARDED", pub.pullRequestNumber(),
                            pub.pullRequestUrl(), "DISCARDED", null));
                });
                database.getAgentRun(activeRunId).ifPresent(ar -> {
                    database.updateAgentRun(ar.runId(), com.cb.auditagent.domain.AgentPhase.DISCARDED,
                            com.cb.auditagent.domain.AgentRunStatus.DISCARDED,
                            ar.iteration(), ar.retryCount(), ar.patchApplied(), ar.compilePassed(), ar.rescanPassed(),
                            ar.testsPassed(), ar.checkpointJson(), ar.finalSummary(), null, null);
                });
            }

            if (!activeRuns.isEmpty()) {
                vulnerability.setStatus(VulnerabilityStatus.DETECTED);
                database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(), null);
            }

            if (vulnerability.getStatus() != VulnerabilityStatus.DETECTED
                    && vulnerability.getStatus() != VulnerabilityStatus.PATCH_FAILED) {
                throw new IllegalStateException(
                        "Finding already has a remediation lifecycle: " + vulnerability.getStatus());
            }

            run = database.createManagedAgentRun(state.getThreadId(), vulnerability, workspace.toString(),
                    agentConfig.getMaxIterations());
            String branchName = "auditagent/fix-" + vulnerability.getId() + "-" + run.runId().substring(0, 6);
            database.createRunPublication(new com.cb.auditagent.domain.RunPublicationRecord(run.runId(),
                    state.getAuthenticatedUser().userId(), repositoryId,
                    repository.installationId(), snapshot.reportKey(), branch, snapshot.baseSha(), workspace.toString(),
                    branchName, null, null, null, null, "NOT_STARTED", null, null, null, null));
        }

        String installationToken = github.installationToken(repository.installationId());
        try {
            sourceControl.cloneAtCommit(repository, branch, snapshot.baseSha(),
                    installationToken, workspaceKey);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String code = message.contains("STALE_BASE") ? "STALE_BASE" : "CLONE_FAILED";
            database.updateAgentRun(run.runId(), com.cb.auditagent.domain.AgentPhase.CONFLICTED,
                    com.cb.auditagent.domain.AgentRunStatus.CONFLICTED, run.iteration(), run.retryCount(),
                    run.patchApplied(), run.compilePassed(), run.rescanPassed(), run.testsPassed(),
                    run.checkpointJson(), run.finalSummary(), code, message);
            vulnerability.setStatus(VulnerabilityStatus.DETECTED);
            database.updateVulnerabilityStatus(vulnerability.getId(), VulnerabilityStatus.DETECTED, null);
            try {
                sourceControl.cleanupWorkspace(workspace);
            } catch (Exception ignored) {
            }
            throw e;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("runId", run.runId());
        updates.put("workspacePath", workspace.toString());
        updates.put("vulnerability", vulnerability);
        updates.put("repoPath", workspace.toString());
        updates.put("repositoryMemoryKey", snapshot.reportKey());
        updates.put("workflowMessage", "Prepared isolated GitHub workspace at " + snapshot.baseSha());

        return updates;
    }
}
