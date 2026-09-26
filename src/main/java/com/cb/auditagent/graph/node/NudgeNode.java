package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;

import java.util.*;

@Component
public class NudgeNode implements NodeAction<RemediationState> {
    private final ConversationMemoryService memoryService;

    public NudgeNode(ConversationMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) {
        String msg = String.format("You have called the tool '%s' multiple times without success. " +
                "Please reconsider your approach. Try reading different parts of the file, or if you " +
                "have gathered enough context, apply a patch.", state.getLastToolName());
        memoryService.addUserMessage(state.getThreadId(), state.getRunId(), msg);

        List<Map<String, Object>> trajectory = state.getTrajectory() != null ? new ArrayList<>(state.getTrajectory())
                : new ArrayList<>();
        Map<String, Object> step = new HashMap<>();
        step.put("iteration", state.getIteration());
        step.put("action", "NUDGE");
        step.put("result", "Agent nudged to change approach");
        trajectory.add(step);

        Map<String, Object> updates = new HashMap<>();
        updates.put("consecutiveSameToolCalls", 0);
        updates.put("trajectory", trajectory);
        return updates;
    }
}
