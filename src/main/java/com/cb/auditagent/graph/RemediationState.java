package com.cb.auditagent.graph;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;

import com.cb.auditagent.domain.Vulnerability;

import java.util.List;
import java.util.Map;

public class RemediationState extends AgentState {

    public RemediationState(Map<String, Object> initData) {
        super(initData);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public String getRunId() {
        return this.<String>value("runId").orElse(null);
    }

    public String getThreadId() {
        return this.<String>value("threadId").orElse(null);
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
        return this.<String>value("repoPath").orElse(null);
    }

    public String getRepositoryMemoryKey() {
        return this.<String>value("repositoryMemoryKey").orElse(null);
    }

    public int getIteration() {
        return this.<Integer>value("iteration").orElse(0);
    }

    public int getMaxIterations() {
        return this.<Integer>value("maxIterations").orElse(15);
    }

    public int getConsecutiveEmptyResponses() {
        return this.<Integer>value("consecutiveEmptyResponses").orElse(0);
    }

    public int getConsecutiveSameToolCalls() {
        return this.<Integer>value("consecutiveSameToolCalls").orElse(0);
    }

    public String getLastToolName() {
        return this.<String>value("lastToolName").orElse(null);
    }

    public boolean isPatchApplied() {
        return this.<Boolean>value("patchApplied").orElse(false);
    }

    public boolean isHasCalledApplyPatch() {
        return this.<Boolean>value("hasCalledApplyPatch").orElse(false);
    }

    public boolean isCompilePassed() {
        return this.<Boolean>value("compilePassed").orElse(false);
    }

    public boolean isRescanPassed() {
        return this.<Boolean>value("rescanPassed").orElse(false);
    }

    public boolean isTestsPassed() {
        return this.<Boolean>value("testsPassed").orElse(false);
    }

    public boolean isTestsAttempted() {
        return this.<Boolean>value("testsAttempted").orElse(false);
    }

    public String getFinalResponse() {
        return this.<String>value("finalResponse").orElse(null);
    }

    public boolean isHitMaxIterations() {
        return this.<Boolean>value("hitMaxIterations").orElse(false);
    }

    public boolean isVerified() {
        return this.<Boolean>value("verified").orElse(false);
    }

    public List<Map<String, Object>> getTrajectory() {
        return this.<List<Map<String, Object>>>value("trajectory").orElse(null);
    }

    public List<ChatMessage> getPendingToolResults() {
        return this.<List<ChatMessage>>value("pendingToolResults").orElse(null);
    }

    public List<String> getAllowedTools() {
        return this.<List<String>>value("allowedTools").orElse(null);
    }

    public String getNextAction() {
        return this.<String>value("nextAction").orElse(null);
    }
}
