package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.ApprovalPreview;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.RunPublicationRecord;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.graph.agent.RemediationOutcomeClassifier;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.PatchBundleService;
import com.cb.auditagent.service.SourceControlProvider;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ApprovalReadyNode implements NodeAction<WorkflowState> {
    private static final Logger logger = LoggerFactory.getLogger(ApprovalReadyNode.class);

    private final DatabaseService database;
    private final SourceControlProvider sourceControl;
    private final PatchBundleService patchBundleService;
    private final AgentConfig agentConfig;

    public ApprovalReadyNode(DatabaseService database, SourceControlProvider sourceControl) {
        this(database, sourceControl, null, new AgentConfig());
    }

    @Autowired
    public ApprovalReadyNode(
            DatabaseService database,
            SourceControlProvider sourceControl,
            @Autowired(required = false) PatchBundleService patchBundleService,
            AgentConfig agentConfig) {
        this.database = database;
        this.sourceControl = sourceControl;
        this.patchBundleService = patchBundleService;
        this.agentConfig = agentConfig != null ? agentConfig : new AgentConfig();
    }

    @SuppressWarnings("unused")
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

        Path workspace = Path.of(publication.workspacePath());
        List<String> files = sourceControl.changedFiles(workspace);

        if (files.isEmpty()) {
            boolean explicitFalsePositive = isExplicitFalsePositive(run, state);
            if (explicitFalsePositive) {
                String summary = run.finalSummary() != null && !run.finalSummary().isBlank()
                        ? run.finalSummary()
                        : "Agent identified finding as a false positive (no changes generated)";
                database.updateAgentRun(
                        run.runId(),
                        AgentPhase.REJECTED,
                        AgentRunStatus.FAILED,
                        run.iteration(),
                        run.retryCount(),
                        run.patchApplied(),
                        run.compilePassed(),
                        run.rescanPassed(),
                        run.testsPassed(),
                        run.checkpointJson(),
                        summary,
                        "FALSE_POSITIVE",
                        null);
                database.getVulnerabilityById(run.vulnerabilityId(), publication.reportKey())
                        .or(() -> database.getVulnerabilityById(run.vulnerabilityId()))
                        .ifPresent(vulnerability -> {
                            vulnerability.setStatus(VulnerabilityStatus.IGNORED);
                            database.updateVulnerabilityStatus(
                                    vulnerability.getId(),
                                    vulnerability.getStatus(),
                                    vulnerability.getProposedFix());
                        });
                throw new IllegalStateException(
                        "Agent identified finding as a false positive (no files changed). Vulnerability has been marked as IGNORED.");
            } else {
                String errorCode = (run.errorCode() != null && !run.errorCode().isBlank())
                        ? run.errorCode()
                        : "NO_CHANGES_GENERATED";
                String errorDetail = (run.errorDetail() != null && !run.errorDetail().isBlank())
                        ? run.errorDetail()
                        : "No files were changed during the remediation run.";
                String failureReason = run.finalSummary() != null && !run.finalSummary().isBlank()
                        ? run.finalSummary()
                        : errorCode;
                database.updateAgentRun(
                        run.runId(),
                        AgentPhase.FAILED,
                        AgentRunStatus.FAILED,
                        run.iteration(),
                        run.retryCount(),
                        run.patchApplied(),
                        run.compilePassed(),
                        run.rescanPassed(),
                        run.testsPassed(),
                        run.checkpointJson(),
                        run.finalSummary(),
                        errorCode,
                        errorDetail);
                database.getVulnerabilityById(run.vulnerabilityId(), publication.reportKey())
                        .or(() -> database.getVulnerabilityById(run.vulnerabilityId()))
                        .ifPresent(vulnerability -> {
                            vulnerability.setStatus(VulnerabilityStatus.PATCH_FAILED);
                            database.updateVulnerabilityStatus(
                                    vulnerability.getId(),
                                    vulnerability.getStatus(),
                                    run.finalSummary() != null ? run.finalSummary()
                                            : vulnerability.getProposedFix());
                        });
                throw new IllegalStateException(
                        "Remediation failed: agent did not produce any code changes (" + failureReason + "). Vulnerability marked as PATCH_FAILED.");
            }
        }

        String diff = sourceControl.workspaceDiff(workspace);
        if (diff.length() > 15000) {
            diff = diff.substring(0, 15000) + "\n... [diff truncated]";
        }

        Map<String, String> verification = database.getVerificationEvidence(runId);
        Map<String, String> hashes = sourceControl.changedFileHashes(workspace);

        String digest = buildDigest(run, publication, hashes, verification);

        RunPublicationRecord updatedPublication = new RunPublicationRecord(
                publication.runId(), publication.userId(), publication.repositoryId(),
                publication.installationId(), publication.reportKey(), publication.baseBranch(),
                publication.baseSha(), publication.workspacePath(), publication.branchName(),
                digest, null, null, null, publication.pushStatus(),
                null, null, null, null);
        database.saveRunPublication(updatedPublication);

        ApprovalPreview preview = new ApprovalPreview(
                runId, run.vulnerabilityId(), repository.fullName(), publication.baseBranch(),
                publication.baseSha(), publication.branchName(), files, diff, verification,
                Optional.ofNullable(run.finalSummary()).orElse("Verified security remediation"),
                digest);

        Map<String, Object> updates = new HashMap<>();
        updates.put("approvalPreview", preview);
        database.getVulnerabilityById(run.vulnerabilityId(), publication.reportKey())
                .ifPresent(vulnerability -> {
                    vulnerability.setStatus(VulnerabilityStatus.AWAITING_APPROVAL);
                    database.updateVulnerabilityStatus(
                            vulnerability.getId(),
                            vulnerability.getStatus(),
                            run.finalSummary() != null ? run.finalSummary()
                                    : vulnerability.getProposedFix());
                });
        database.updateAgentRun(run.runId(), AgentPhase.AWAITING_APPROVAL, AgentRunStatus.AWAITING_APPROVAL,
                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                run.rescanPassed(),
                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);

        if (patchBundleService != null) {
            try {
                patchBundleService.createAndUploadBundle(
                        run.runId(),
                        run.vulnerabilityId(),
                        publication.repositoryId(),
                        publication.baseBranch(),
                        publication.baseSha(),
                        publication.branchName(),
                        workspace,
                        run.finalSummary());
            } catch (Exception e) {
                logger.warn("Failed to create/upload patch bundle for run [{}]: {}", runId, e.getMessage());
            }
        }

        if (agentConfig.isStatelessWorkspaces()) {
            try {
                sourceControl.cleanupWorkspace(workspace);
                logger.info("Stateless workspaces enabled: cleaned up local workspace [{}] for run [{}]", workspace, runId);
            } catch (Exception e) {
                logger.warn("Failed to clean up workspace [{}] for run [{}]: {}", workspace, runId, e.getMessage());
            }
        }

        updates.put("workflowMessage", "Remediation verified. Awaiting human approval.");
        // Decision is NOT set here. The graph pauses at the conditional edge.
        // Human approval comes via POST /api/runs/{runId}/decision ->
        // RemediationWorkflowService.decide()
        return updates;
    }

    private String buildDigest(AgentRunRecord run, RunPublicationRecord pub, Map<String, String> hashes,
            Map<String, String> verify) {
        String data = run.vulnerabilityId() + "|" + pub.baseSha() + "|" + pub.branchName() + "|"
                + hashes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted()
                        .collect(Collectors.joining(","))
                + "|" + verify.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted()
                        .collect(Collectors.joining(","));
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isExplicitFalsePositive(AgentRunRecord run, WorkflowState state) {
        // 1. If run was aborted, timed out, interrupted, conflicted, or discarded, it is NOT a false positive.
        // Note: CompleteNode records FAILED + VERIFICATION_INCOMPLETE whenever no patch was applied;
        // allow false-positive text detection only for that status when no failure indicators are present.
        if (run.phase() == AgentPhase.INTERRUPTED
                || run.phase() == AgentPhase.CONFLICTED
                || run.phase() == AgentPhase.DISCARDED) {
            return false;
        }

        if (run.status() == AgentRunStatus.INTERRUPTED
                || run.status() == AgentRunStatus.CONFLICTED
                || run.status() == AgentRunStatus.DISCARDED) {
            return false;
        }

        boolean verificationIncompleteOnly = "VERIFICATION_INCOMPLETE".equalsIgnoreCase(run.errorCode());
        if ((run.phase() == AgentPhase.FAILED || run.status() == AgentRunStatus.FAILED)
                && !verificationIncompleteOnly) {
            return false;
        }

        if (run.errorCode() != null && !run.errorCode().isBlank() && !verificationIncompleteOnly) {
            return false;
        }

        if (state.isHitMaxIterations()) {
            return false;
        }

        Object nextAgent = state.data().get("nextAgent");
        if (nextAgent != null && "FAIL".equalsIgnoreCase(String.valueOf(nextAgent))) {
            return false;
        }

        // 2. Check if summary or state responses contain abort/timeout/exhaustion indicators
        String summary = run.finalSummary();
        if (RemediationOutcomeClassifier.containsFailureIndicators(summary)) {
            return false;
        }

        String finalResponse = state.getFinalResponse();
        if (RemediationOutcomeClassifier.containsFailureIndicators(finalResponse)) {
            return false;
        }

        // 3. Check for explicit assertion of false positive in summary, response, or agent outputs
        return RemediationOutcomeClassifier.containsExplicitFalsePositiveAssertion(summary)
                || RemediationOutcomeClassifier.containsExplicitFalsePositiveAssertion(finalResponse)
                || containsExplicitFalsePositiveAssertionInOutputs(state);
    }

    private boolean containsExplicitFalsePositiveAssertionInOutputs(WorkflowState state) {
        Object outputs = state.data().get("agentOutputs");
        return outputs instanceof Map<?, ?> map
                && RemediationOutcomeClassifier.hasExplicitFalsePositiveInOutputs(map);
    }
}
