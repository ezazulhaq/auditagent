package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.RunPublicationRecord;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.service.DatabaseService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
public class RejectNode implements NodeAction<WorkflowState> {

    private final DatabaseService database;

    public RejectNode(DatabaseService database) {
        this.database = database;
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) throws Exception {
        String runId = state.getRunId();
        AuthenticatedUser user = state.getAuthenticatedUser();

        AgentRunRecord run = database.getAgentRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));

        RunPublicationRecord publication = database.getRunPublication(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed run"));

        if (run.status() != AgentRunStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Only an awaiting-approval run can be rejected");
        }

        database.updateAgentRun(run.runId(), AgentPhase.REJECTED, AgentRunStatus.REJECTED,
                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);

        database.getVulnerabilityById(run.vulnerabilityId()).ifPresent(vulnerability -> {
            vulnerability.setStatus(VulnerabilityStatus.DETECTED);
            database.updateVulnerabilityStatus(vulnerability.getId(), vulnerability.getStatus(),
                    vulnerability.getProposedFix());
        });

        RunPublicationRecord rejectedPub = new RunPublicationRecord(
                publication.runId(), publication.userId(), publication.repositoryId(),
                publication.installationId(), publication.reportKey(), publication.baseBranch(),
                publication.baseSha(), publication.workspacePath(), publication.branchName(),
                publication.approvalDigest(), user.login(), LocalDateTime.now(ZoneOffset.UTC),
                null, "REJECTED", null, null, "REJECTED", null);
        database.saveRunPublication(rejectedPub);

        Map<String, Object> updates = new HashMap<>();
        updates.put("runStatus", "REJECTED");
        updates.put("workflowMessage", "Fix rejected. No branch or pull request was created.");
        return updates;
    }
}
