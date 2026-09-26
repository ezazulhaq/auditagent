package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.LlmService;

import java.util.Collections;
import java.util.Map;

@Component
public class CompleteNode implements NodeAction<RemediationState> {
    private final LlmService llmService;

    public CompleteNode(@Lazy LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) {
        boolean verified = state.isPatchApplied() && state.isRescanPassed();
        AgentPhase phase = verified ? AgentPhase.AWAITING_APPROVAL : AgentPhase.FAILED;
        AgentRunStatus status = verified ? AgentRunStatus.AWAITING_APPROVAL : AgentRunStatus.FAILED;
        String errorCode = verified ? null : "VERIFICATION_INCOMPLETE";
        llmService.checkpointRun(state.getRunId(), state.getIteration(), state.isPatchApplied(),
                state.isCompilePassed(), state.isRescanPassed(), state.isTestsPassed(),
                phase, status, "LOOP_COMPLETE", state.getFinalResponse(), errorCode);
        return Collections.emptyMap();
    }
}
