package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class VerificationGateNode implements NodeAction<RemediationState> {
    private static final Logger logger = LoggerFactory.getLogger(VerificationGateNode.class);

    private final ConversationMemoryService memoryService;

    public VerificationGateNode(ConversationMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) throws Exception {
        logger.info("Executing VerificationGateNode for run {}", state.getRunId());

        boolean patchApplied = state.isPatchApplied();
        boolean compilePassed = state.isCompilePassed();
        boolean rescanPassed = state.isRescanPassed();
        boolean testsAttempted = state.isTestsAttempted();
        boolean testsPassed = state.isTestsPassed();

        List<Map<String, Object>> trajectory = state.getTrajectory() != null ? new ArrayList<>(state.getTrajectory())
                : new ArrayList<>();

        if (patchApplied && (!compilePassed || !rescanPassed || !testsAttempted || !testsPassed)) {
            String missing = "The run cannot complete yet. Required verification is missing or failed: " +
                    "compile=" + compilePassed + ", target-rescan=" + rescanPassed +
                    ", tests=" + (testsAttempted ? testsPassed : "not-run") +
                    ". Continue with verification or repair the patch.";

            memoryService.addUserMessage(state.getThreadId(), state.getRunId(), missing);

            Map<String, Object> step = new HashMap<>();
            step.put("iteration", state.getIteration());
            step.put("action", "VERIFICATION_GATE");
            step.put("result", missing);
            trajectory.add(step);

            Map<String, Object> updates = new HashMap<>();
            updates.put("verified", false);
            updates.put("trajectory", trajectory);
            return updates;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("verified", true);
        return updates;
    }
}
