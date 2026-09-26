package com.cb.auditagent.graph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.stereotype.Service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.Skill;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.agent.WorkerAgentGraphFactory;
import com.cb.auditagent.graph.node.AbortNode;
import com.cb.auditagent.graph.node.CompleteNode;
import com.cb.auditagent.graph.node.InitNode;
import com.cb.auditagent.graph.node.SupervisorNode;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.SkillManagerService;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

import java.util.HashMap;
import java.util.Map;

@Service
public class MultiAgentRemediationGraph {

    private final CompiledGraph<MultiAgentState> graph;

    public MultiAgentRemediationGraph(
            AgentConfig config,
            BaseCheckpointSaver checkpointSaver,
            InitNode initNode,
            SupervisorNode supervisorNode,
            WorkerAgentGraphFactory workerFactory,
            CompleteNode completeNode,
            AbortNode abortNode,
            ConversationMemoryService memoryService,
            SkillManagerService skillManager) throws Exception {

        StateGraph<MultiAgentState> builder = new StateGraph<>(MultiAgentState::new);

        builder.addNode("init", node_async(state -> {
            RemediationState remState = new RemediationState(state.data());
            return initNode.apply(remState);
        }));

        builder.addNode("supervisor", node_async(supervisorNode));

        // Generate routing map for conditional edges
        Map<String, String> routingMap = new HashMap<>();
        routingMap.put("COMPLETE", "complete");
        routingMap.put("FAIL", "fail");

        // Add dynamic agent nodes for all stages (including PLANNING)
        for (AgentStage stage : AgentStage.values()) {
            String nodeName = stage.name();
            routingMap.put(nodeName, nodeName);

            // Build the graph once for this stage
            CompiledGraph<RemediationState> stageGraph = workerFactory.createWorkerGraph(stage);

            builder.addNode(nodeName, node_async(state -> {
                String runId = state.getRunId();
                String threadId = state.getThreadId();

                Skill skill = skillManager.getSkill(stage.getSkillName());
                String skillInstructions = skill != null ? skill.getInstructions()
                        : "Fallback default instructions for this stage.";
                String instructions = String.format("You are now the %s agent. %s", stage.getStageName(),
                        skillInstructions);

                memoryService.addUserMessage(threadId, runId, instructions);

                Map<String, Object> subStateMap = new HashMap<>(state.data());
                subStateMap.put("allowedTools", stage.getAllowedTools());
                subStateMap.put("maxIterations", config.getMaxIterations());
                subStateMap.put("iteration", 0);

                RemediationState result = stageGraph
                        .invoke(GraphInput.args(subStateMap),
                                RunnableConfig.builder().build())
                        .orElse(new RemediationState(subStateMap));

                // Track output for supervisor
                Map<String, Object> updates = new HashMap<>(result.data());
                Map<String, String> agentOutputs = new HashMap<>(state.getAgentOutputs());
                String finalResponse = result.getFinalResponse() != null ? result.getFinalResponse()
                        : "Completed without specific message.";
                agentOutputs.put(nodeName, finalResponse);
                updates.put("agentOutputs", agentOutputs);

                // Special handling for PLANNING stage: mark plan as generated
                if (stage == AgentStage.PLANNING) {
                    updates.put("planGenerated", true);
                    updates.put("remediationPlan", finalResponse);
                }

                return updates;
            }));

            // Link each worker back to the supervisor
            builder.addEdge(nodeName, "supervisor");
        }

        builder.addNode("complete", node_async(state -> {
            RemediationState remState = new RemediationState(state.data());
            return completeNode.apply(remState);
        }));

        builder.addNode("fail", node_async(state -> {
            RemediationState remState = new RemediationState(state.data());
            return abortNode.apply(remState);
        }));

        builder.addEdge(StateGraph.START, "init");
        builder.addEdge("init", "supervisor");

        // Conditional router from supervisor
        builder.addConditionalEdges("supervisor", edge_async(state -> state.getNextAgent()), routingMap);

        builder.addEdge("complete", StateGraph.END);
        builder.addEdge("fail", StateGraph.END);

        this.graph = builder.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .recursionLimit(150)
                .build());
    }

    public CompiledGraph<MultiAgentState> getGraph() {
        return graph;
    }
}
