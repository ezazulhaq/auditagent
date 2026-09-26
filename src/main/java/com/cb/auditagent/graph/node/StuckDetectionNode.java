package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.graph.RemediationState;

import java.util.Collections;
import java.util.Map;

@Component
public class StuckDetectionNode implements NodeAction<RemediationState> {
    @Override
    public Map<String, Object> apply(RemediationState state) {
        return Collections.emptyMap(); // Routing handled by ConditionalEdge
    }
}
