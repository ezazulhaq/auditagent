package com.cb.auditagent.graph.agent;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.stereotype.Service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.graph.node.*;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkerAgentGraphFactory {

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("\"(?:file_path|filePath)\"\\s*:\\s*\"([^\"]+)\"");

    private final AgentConfig config;
    private final BaseCheckpointSaver checkpointSaver;
    private final ModelCallNode modelCallNode;
    private final ToolExecutionNode toolExecutionNode;
    private final StuckDetectionNode stuckDetectionNode;
    private final NudgeNode nudgeNode;
    private final EmptyResponseNode emptyResponseNode;
    private final TimeoutNode timeoutNode;

    public WorkerAgentGraphFactory(
            AgentConfig config,
            BaseCheckpointSaver checkpointSaver,
            ModelCallNode modelCallNode,
            ToolExecutionNode toolExecutionNode,
            StuckDetectionNode stuckDetectionNode,
            NudgeNode nudgeNode,
            EmptyResponseNode emptyResponseNode,
            TimeoutNode timeoutNode) {
        this.config = config;
        this.checkpointSaver = checkpointSaver;
        this.modelCallNode = modelCallNode;
        this.toolExecutionNode = toolExecutionNode;
        this.stuckDetectionNode = stuckDetectionNode;
        this.nudgeNode = nudgeNode;
        this.emptyResponseNode = emptyResponseNode;
        this.timeoutNode = timeoutNode;
    }

    public CompiledGraph<RemediationState> createWorkerGraph(AgentStage stage) throws Exception {
        StateGraph<RemediationState> builder = new StateGraph<>(RemediationState::new);

        builder.addNode("call_model", node_async(modelCallNode))
                .addNode("execute_tools", node_async(toolExecutionNode))
                .addNode("stuck_detection", node_async(stuckDetectionNode))
                .addNode("nudge", node_async(nudgeNode))
                .addNode("empty_response", node_async(emptyResponseNode))
                .addNode("timeout_handler", node_async(timeoutNode))
                .addNode("record_milestone", node_async(state -> {
                    StageMilestone milestone = createMilestone(stage, state);
                    return Map.of("currentMilestone", milestone);
                }));

        builder.addEdge(StateGraph.START, "call_model")
                .addConditionalEdges("call_model", edge_async(state -> {
                    String nextAction = state.getNextAction();
                    if ("timeout".equals(nextAction))
                        return "timeout_handler";
                    if ("empty".equals(nextAction))
                        return "empty_response";
                    if ("tools".equals(nextAction))
                        return "execute_tools";
                    return "record_milestone";
                }), Map.of(
                        "timeout_handler", "timeout_handler",
                        "empty_response", "empty_response",
                        "execute_tools", "execute_tools",
                        "record_milestone", "record_milestone"))
                .addEdge("execute_tools", "stuck_detection")
                .addConditionalEdges("stuck_detection", edge_async(state -> {
                    if (state.getIteration() >= state.getMaxIterations()) {
                        return "record_milestone";
                    }
                    return state.getConsecutiveSameToolCalls() >= 3 ? "nudge" : "call_model";
                }), Map.of("record_milestone", "record_milestone", "nudge", "nudge", "call_model", "call_model"))
                .addEdge("nudge", "call_model")
                .addConditionalEdges("empty_response", edge_async(
                        state -> state.getConsecutiveEmptyResponses() >= config.getMaxConsecutiveEmptyResponses()
                                ? "record_milestone"
                                : "call_model"),
                        Map.of("record_milestone", "record_milestone", "call_model", "call_model"))
                .addEdge("timeout_handler", "call_model")
                .addEdge("record_milestone", StateGraph.END);

        return builder.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .releaseThread(false)
                .recursionLimit(100)
                .build());
    }

    public StageMilestone createMilestone(AgentStage stage, RemediationState state) {
        boolean success = evaluateStageSuccess(stage, state);
        List<String> touchedFiles = extractTouchedFiles(state);
        String summary = extractSummary(stage, state, success);
        return new StageMilestone(stage, success, summary, touchedFiles);
    }

    private boolean evaluateStageSuccess(AgentStage stage, RemediationState state) {
        if (state.isHitMaxIterations()) {
            return false;
        }

        switch (stage) {
            case PLANNING:
                return state.getFinalResponse() != null && !state.getFinalResponse().isBlank();
            case ROOT_CAUSE:
                return state.isPatchApplied();
            case POST_FIX:
                return state.isCompilePassed() && state.isRescanPassed()
                        && (!state.isTestsAttempted() || state.isTestsPassed());
            case BASELINE:
            case SYNTAX:
                if (state.isTestsAttempted() && !state.isTestsPassed()) {
                    return false;
                }
                return state.isCompilePassed() || state.getIteration() > 0;
            default:
                String finalResponse = state.getFinalResponse();
                if (finalResponse != null) {
                    String lower = finalResponse.toLowerCase(Locale.ROOT);
                    if (lower.contains("failed") && (lower.contains("fatal") || lower.contains("unable to fix") || lower.contains("critical error"))) {
                        return false;
                    }
                }
                return true;
        }
    }

    private List<String> extractTouchedFiles(RemediationState state) {
        Set<String> files = new LinkedHashSet<>();

        if (state.getVulnerability() != null && state.getVulnerability().getFilePath() != null) {
            String path = state.getVulnerability().getFilePath().trim();
            if (!path.isBlank()) {
                files.add(path);
            }
        }

        List<Map<String, Object>> trajectory = state.getTrajectory();
        if (trajectory != null) {
            for (Map<String, Object> step : trajectory) {
                Object argsObj = step.get("args");
                if (argsObj instanceof String argsStr) {
                    Matcher m = FILE_PATH_PATTERN.matcher(argsStr);
                    if (m.find()) {
                        files.add(m.group(1).trim());
                    }
                }
            }
        }

        return new ArrayList<>(files);
    }

    private String extractSummary(AgentStage stage, RemediationState state, boolean success) {
        String finalResponse = state.getFinalResponse();
        if (finalResponse != null && !finalResponse.isBlank()) {
            return truncateToTokens(finalResponse.replaceAll("\\s+", " ").trim(), 150);
        }

        List<Map<String, Object>> trajectory = state.getTrajectory();
        int toolCount = trajectory != null ? trajectory.size() : 0;
        return String.format("%s executed %d tool actions; outcome: %s.",
                stage.getStageName(), toolCount, success ? "SUCCESS" : "INCOMPLETE");
    }

    private String truncateToTokens(String text, int maxTokens) {
        if (text == null) {
            return "";
        }
        String[] words = text.split("\\s+");
        if (words.length <= maxTokens) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxTokens; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        sb.append("... [truncated]");
        return sb.toString();
    }
}
