package com.cb.auditagent.graph.workflow;

import java.util.Map;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.graph.RemediationState;

import java.util.HashMap;

public class WorkflowState extends RemediationState {

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public WorkflowState() {
        super(new HashMap<>());
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AuthenticatedUser getAuthenticatedUser() {
        Object val = data().get("authenticatedUser");
        if (val instanceof AuthenticatedUser)
            return (AuthenticatedUser) val;
        if (val instanceof Map)
            return mapper.convertValue(val, AuthenticatedUser.class);
        return null;
    }

    public long getRepositoryId() {
        Object val = data().get("repositoryId");
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

    public String getBranch() {
        return this.<String>value("branch").orElse(null);
    }

    public String getVulnerabilityId() {
        return this.<String>value("vulnerabilityId").orElse(null);
    }

    public String getDecision() {
        return this.<String>value("decision").orElse(null);
    }

    public String getSuppliedDigest() {
        return this.<String>value("suppliedDigest").orElse(null);
    }

    public String getWorkspacePath() {
        return this.<String>value("workspacePath").orElse(null);
    }

    public String getPullRequestUrl() {
        return this.<String>value("pullRequestUrl").orElse(null);
    }

    public String getRunStatus() {
        return this.<String>value("runStatus").orElse(null);
    }

    public Integer getPullRequestNumber() {
        return this.<Integer>value("pullRequestNumber").orElse(null);
    }

    public String getWorkflowMessage() {
        return this.<String>value("workflowMessage").orElse(null);
    }

    public com.cb.auditagent.domain.ApprovalPreview getApprovalPreview() {
        Object val = data().get("approvalPreview");
        if (val instanceof com.cb.auditagent.domain.ApprovalPreview)
            return (com.cb.auditagent.domain.ApprovalPreview) val;
        if (val instanceof Map)
            return mapper.convertValue(val, com.cb.auditagent.domain.ApprovalPreview.class);
        return null;
    }
}
