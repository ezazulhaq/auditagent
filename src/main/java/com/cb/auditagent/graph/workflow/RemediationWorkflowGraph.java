package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import java.util.Map;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.DuckDbCheckpointSaver;
import com.cb.auditagent.graph.MultiAgentRemediationGraph;
import com.cb.auditagent.graph.RemediationGraph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Component
public class RemediationWorkflowGraph {

    private final ValidateAccessNode validateAccessNode;
    private final CloneWorkspaceNode cloneWorkspaceNode;
    private final ApprovalReadyNode approvalReadyNode;
    private final RejectNode rejectNode;
    private final PublicationNode publicationNode;
    private final RemediationGraph remediationGraph;
    private final MultiAgentRemediationGraph multiAgentRemediationGraph;
    private final DuckDbCheckpointSaver checkpointSaver;
    private final AgentConfig agentConfig;

    private final CompiledGraph<WorkflowState> graph;

    public RemediationWorkflowGraph(
            ValidateAccessNode validateAccessNode,
            CloneWorkspaceNode cloneWorkspaceNode,
            ApprovalReadyNode approvalReadyNode,
            RejectNode rejectNode,
            PublicationNode publicationNode,
            RemediationGraph remediationGraph,
            MultiAgentRemediationGraph multiAgentRemediationGraph,
            DuckDbCheckpointSaver checkpointSaver,
            AgentConfig agentConfig) throws Exception {

        this.validateAccessNode = validateAccessNode;
        this.cloneWorkspaceNode = cloneWorkspaceNode;
        this.approvalReadyNode = approvalReadyNode;
        this.rejectNode = rejectNode;
        this.publicationNode = publicationNode;
        this.remediationGraph = remediationGraph;
        this.multiAgentRemediationGraph = multiAgentRemediationGraph;
        this.checkpointSaver = checkpointSaver;
        this.agentConfig = agentConfig;

        StateGraph<WorkflowState> workflow = new StateGraph<>(WorkflowState::new);

        workflow.addNode("validate_access", node_async(validateAccessNode));
        workflow.addNode("clone_workspace", node_async(cloneWorkspaceNode));

        workflow.addNode("agent_loop", node_async(state -> {
            try {
                if (agentConfig.isUseMultiAgent()) {
                    var config = org.bsc.langgraph4j.RunnableConfig.builder().build();
                    com.cb.auditagent.graph.state.MultiAgentState result = multiAgentRemediationGraph.getGraph()
                            .invoke(org.bsc.langgraph4j.GraphInput.args(state.data()), config)
                            .orElse(new com.cb.auditagent.graph.state.MultiAgentState(state.data()));
                    return result.data();
                } else {
                    var config = org.bsc.langgraph4j.RunnableConfig.builder().build();
                    com.cb.auditagent.graph.RemediationState result = remediationGraph.getGraph()
                            .invoke(org.bsc.langgraph4j.GraphInput.args(state.data()), config)
                            .orElse(new com.cb.auditagent.graph.RemediationState(state.data()));
                    return result.data();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        workflow.addNode("approval_ready", node_async(approvalReadyNode));
        workflow.addNode("process_decision", node_async(state -> state.data()));
        workflow.addNode("reject", node_async(rejectNode));
        workflow.addNode("publish", node_async(publicationNode));

        workflow.addEdge(START, "validate_access");
        workflow.addEdge("validate_access", "clone_workspace");
        workflow.addEdge("clone_workspace", "agent_loop");
        workflow.addEdge("agent_loop", "approval_ready");
        workflow.addEdge("approval_ready", "process_decision");

        workflow.addConditionalEdges("process_decision",
                org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async(state -> {
                    String decision = ((WorkflowState) state).getDecision();
                    if ("REJECT".equals(decision))
                        return "reject";
                    if ("APPROVE_AND_CREATE_PR".equals(decision))
                        return "publish";
                    return "end";
                }),
                Map.of("reject", "reject", "publish", "publish", "end", END));

        workflow.addEdge("reject", END);
        workflow.addEdge("publish", END);

        // Pause before process_decision so the graph waits for human input
        // before evaluating the conditional edge.
        this.graph = workflow.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore("process_decision")
                .build());
    }

    public CompiledGraph<WorkflowState> getGraph() {
        return graph;
    }
}
