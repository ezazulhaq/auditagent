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

import java.util.Map;

@Service
public class WorkerAgentGraphFactory {

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
                .addNode("timeout_handler", node_async(timeoutNode));

        builder.addEdge(StateGraph.START, "call_model")
                .addConditionalEdges("call_model", edge_async(state -> {
                    String nextAction = state.getNextAction();
                    if ("timeout".equals(nextAction))
                        return "timeout_handler";
                    if ("empty".equals(nextAction))
                        return "empty_response";
                    if ("tools".equals(nextAction))
                        return "execute_tools";
                    return "complete";
                }), Map.of(
                        "timeout_handler", "timeout_handler",
                        "empty_response", "empty_response",
                        "execute_tools", "execute_tools",
                        "complete", StateGraph.END))
                .addEdge("execute_tools", "stuck_detection")
                .addConditionalEdges("stuck_detection", edge_async(state -> {
                    if (state.getIteration() >= state.getMaxIterations()) {
                        return "complete";
                    }
                    return state.getConsecutiveSameToolCalls() >= 3 ? "nudge" : "call_model";
                }), Map.of("complete", StateGraph.END, "nudge", "nudge", "call_model", "call_model"))
                .addEdge("nudge", "call_model")
                .addConditionalEdges("empty_response", edge_async(
                        state -> state.getConsecutiveEmptyResponses() >= config.getMaxConsecutiveEmptyResponses()
                                ? "complete"
                                : "call_model"),
                        Map.of("complete", StateGraph.END, "call_model", "call_model"))
                .addEdge("timeout_handler", "call_model");

        return builder.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .recursionLimit(100)
                .build());
    }
}
