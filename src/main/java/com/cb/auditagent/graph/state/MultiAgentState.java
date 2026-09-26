package com.cb.auditagent.graph.state;

import com.cb.auditagent.domain.Vulnerability;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bsc.langgraph4j.state.AgentState;
import java.util.Map;

public class MultiAgentState extends AgentState {
    public MultiAgentState(Map<String, Object> initData) {
        super(initData);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public String getRunId() {
        return (String) data().get("runId");
    }

    public String getThreadId() {
        return (String) data().get("threadId");
    }

    public Vulnerability getVulnerability() {
        Object val = data().get("vulnerability");
        if (val instanceof Vulnerability)
            return (Vulnerability) val;
        if (val instanceof Map)
            return mapper.convertValue(val, Vulnerability.class);
        return null;
    }

    public String getRepoPath() {
        return (String) data().get("repoPath");
    }

    public String getRepositoryMemoryKey() {
        return (String) data().get("repositoryMemoryKey");
    }

    public String getHandoffDirection() {
        return (String) data().get("handoffDirection");
    }

    public String getHandoffContext() {
        return (String) data().get("handoffContext");
    }

    public String getNextAgent() {
        return (String) data().get("nextAgent");
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAgentOutputs() {
        Object val = data().get("agentOutputs");
        return val instanceof Map ? (Map<String, String>) val : java.util.Collections.emptyMap();
    }

    public int getRetries() {
        Object val = data().get("retries");
        return val instanceof Integer ? (Integer) val : 0;
    }

    public boolean isPatchApplied() {
        Object val = data().get("patchApplied");
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public boolean isCompilePassed() {
        Object val = data().get("compilePassed");
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public boolean isRescanPassed() {
        Object val = data().get("rescanPassed");
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public boolean isTestsPassed() {
        Object val = data().get("testsPassed");
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public boolean isPlanGenerated() {
        Object val = data().get("planGenerated");
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public String getRemediationPlan() {
        Object val = data().get("remediationPlan");
        return val instanceof String ? (String) val : null;
    }
}
