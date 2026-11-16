package com.cb.auditagent.graph.state;

import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.graph.agent.StageMilestone;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bsc.langgraph4j.state.AgentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MultiAgentState extends AgentState {
    public MultiAgentState(Map<String, Object> initData) {
        super(initData);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public String getRunId() {
        return (String) data().get("runId");
    }

    public String getUserId() {
        Object u = data().get("userId");
        if (u instanceof String str && !str.isBlank()) return str;
        Object authUser = data().get("authenticatedUser");
        if (authUser instanceof com.cb.auditagent.domain.AuthenticatedUser user) return user.userId();
        if (authUser instanceof Map map && map.get("userId") != null) return map.get("userId").toString();
        return null;
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

    public int getHops() {
        Object val = data().get("hops");
        if (val instanceof Integer i) {
            return i;
        }
        return getRetries();
    }

    @SuppressWarnings("unchecked")
    public List<StageMilestone> getMilestones() {
        Object val = data().get("milestones");
        if (val instanceof List<?> list) {
            List<StageMilestone> milestones = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof StageMilestone sm) {
                    milestones.add(sm);
                } else if (item instanceof Map<?, ?> map) {
                    milestones.add(mapper.convertValue(map, StageMilestone.class));
                }
            }
            return Collections.unmodifiableList(milestones);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getStageRetries() {
        Object val = data().get("stageRetries");
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Integer>) map;
        }
        return Collections.emptyMap();
    }

    public String getLastStage() {
        return (String) data().get("lastStage");
    }

    public Boolean getLastStageSuccess() {
        Object val = data().get("lastStageSuccess");
        return val instanceof Boolean ? (Boolean) val : null;
    }

    public String getSupervisorReasoning() {
        return (String) data().get("supervisorReasoning");
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
