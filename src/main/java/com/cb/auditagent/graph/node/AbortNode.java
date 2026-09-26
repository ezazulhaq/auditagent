package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.LlmService;

import java.util.HashMap;
import java.util.Map;

@Component
public class AbortNode implements NodeAction<RemediationState> {
    private final LlmService llmService;

    public AbortNode(@Lazy LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) {
        String finalResponse = "Agent aborted after reaching max iterations or max consecutive errors.";
        llmService.checkpointRun(state.getRunId(), state.getIteration(), state.isPatchApplied(),
                state.isCompilePassed(), state.isRescanPassed(), state.isTestsPassed(),
                AgentPhase.FAILED, AgentRunStatus.FAILED, "FAILED", finalResponse, "ABORTED");
        Map<String, Object> updates = new HashMap<>();
        updates.put("finalResponse", finalResponse);
        return updates;
    }
}
