package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;

import java.util.*;

@Component
public class EmptyResponseNode implements NodeAction<RemediationState> {
    private final ConversationMemoryService memoryService;

    public EmptyResponseNode(ConversationMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) {
        int consecutive = state.getConsecutiveEmptyResponses() + 1;
        memoryService.addUserMessage(state.getThreadId(), state.getRunId(),
                "The previous LLM call returned an empty response. Please try again.");

        List<Map<String, Object>> trajectory = state.getTrajectory() != null ? new ArrayList<>(state.getTrajectory())
                : new ArrayList<>();
        Map<String, Object> step = new HashMap<>();
        step.put("iteration", state.getIteration());
        step.put("action", "EMPTY_RESPONSE");
        step.put("result", "Empty response #" + consecutive);
        trajectory.add(step);

        Map<String, Object> updates = new HashMap<>();
        updates.put("consecutiveEmptyResponses", consecutive);
        updates.put("trajectory", trajectory);
        return updates;
    }
}
