package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.graph.RemediationState;

import java.util.*;

@Component
public class TimeoutNode implements NodeAction<RemediationState> {
    @Override
    public Map<String, Object> apply(RemediationState state) {
        List<Map<String, Object>> trajectory = state.getTrajectory() != null ? new ArrayList<>(state.getTrajectory())
                : new ArrayList<>();
        Map<String, Object> step = new HashMap<>();
        step.put("iteration", state.getIteration());
        step.put("action", "TIMEOUT");
        step.put("result", "LLM call timed out");
        trajectory.add(step);

        Map<String, Object> updates = new HashMap<>();
        updates.put("trajectory", trajectory);
        return updates;
    }
}
