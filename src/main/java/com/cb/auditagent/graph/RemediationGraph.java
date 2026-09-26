package com.cb.auditagent.graph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.stereotype.Service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.node.*;

import java.util.Map;

@Service
public class RemediationGraph {

    private final CompiledGraph<RemediationState> graph;

    public RemediationGraph(
            AgentConfig config,
            BaseCheckpointSaver checkpointSaver,
            InitNode initNode,
            ModelCallNode modelCallNode,
            ToolExecutionNode toolExecutionNode,
            StuckDetectionNode stuckDetectionNode,
            NudgeNode nudgeNode,
            VerificationGateNode verificationGateNode,
            EmptyResponseNode emptyResponseNode,
            TimeoutNode timeoutNode,
            CompleteNode completeNode,
            AbortNode abortNode) throws Exception {

        StateGraph<RemediationState> builder = new StateGraph<>(RemediationState::new);

        // Add Nodes
        builder.addNode("init", node_async(initNode))
                .addNode("call_model", node_async(modelCallNode))
                .addNode("execute_tools", node_async(toolExecutionNode))
                .addNode("stuck_detection", node_async(stuckDetectionNode))
                .addNode("nudge", node_async(nudgeNode))
                .addNode("verification_gate", node_async(verificationGateNode))
                .addNode("empty_response", node_async(emptyResponseNode))
                .addNode("timeout_handler", node_async(timeoutNode))
                .addNode("complete", node_async(completeNode))
                .addNode("abort", node_async(abortNode));

        // Edges
        builder.addEdge(StateGraph.START, "init")
                .addEdge("init", "call_model")
                .addConditionalEdges("call_model", edge_async(state -> {
                    String nextAction = state.getNextAction();
                    if ("timeout".equals(nextAction))
                        return "timeout_handler";
                    if ("empty".equals(nextAction))
                        return "empty_response";
                    if ("tools".equals(nextAction))
                        return "execute_tools";
                    return "verification_gate"; // prose response
                }), Map.of(
                        "timeout_handler", "timeout_handler",
                        "empty_response", "empty_response",
                        "execute_tools", "execute_tools",
                        "verification_gate", "verification_gate"))
                .addEdge("execute_tools", "stuck_detection")
                .addConditionalEdges("stuck_detection",
                        edge_async(state -> state.getConsecutiveSameToolCalls() >= 3 ? "nudge" : "budget_check"),
                        Map.of("nudge", "nudge", "budget_check", "call_model"))
                .addConditionalEdges("nudge",
                        edge_async(state -> state.getIteration() >= state.getMaxIterations() ? "abort" : "call_model"),
                        Map.of("abort", "abort", "call_model", "call_model"))
                .addConditionalEdges("verification_gate",
                        edge_async(state -> state.isVerified() ? "complete" : "call_model"),
                        Map.of("complete", "complete", "call_model", "call_model"))
                .addConditionalEdges("empty_response", edge_async(
                        state -> state.getConsecutiveEmptyResponses() >= config.getMaxConsecutiveEmptyResponses()
                                ? "abort"
                                : "call_model"),
                        Map.of("abort", "abort", "call_model", "call_model"))
                .addEdge("timeout_handler", "call_model")
                .addEdge("complete", StateGraph.END)
                .addEdge("abort", StateGraph.END);

        this.graph = builder.compile(CompileConfig.builder().checkpointSaver(checkpointSaver).build());
    }

    public CompiledGraph<RemediationState> getGraph() {
        return graph;
    }
}
