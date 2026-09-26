package com.cb.auditagent.graph.node;

import org.springframework.context.annotation.Lazy;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SupervisorNode implements NodeAction<MultiAgentState> {
    private static final Logger logger = LoggerFactory.getLogger(SupervisorNode.class);

    private final ChatModel chatModel;
    private final ConversationMemoryService memoryService;
    private final AgentConfig config;
    private final LlmService llmService;

    public SupervisorNode(
            ChatModel chatModel,
            ConversationMemoryService memoryService,
            AgentConfig config,
            @Lazy LlmService llmService) {
        this.chatModel = chatModel;
        this.memoryService = memoryService;
        this.config = config;
        this.llmService = llmService;
    }

    @Override
    public Map<String, Object> apply(MultiAgentState state) throws Exception {
        String runId = state.getRunId();
        String threadId = state.getThreadId();
        int iteration = state.getRetries();

        if (iteration >= config.getMaxIterations()) {
            logger.warn("Supervisor hit max iterations: {}", iteration);
            return Map.of("nextAgent", "FAIL");
        }

        // Enforce PLANNING as the mandatory first stage
        if (!state.isPlanGenerated()) {
            logger.info("Supervisor: No plan generated yet. Routing to PLANNING stage.");
            memoryService.addUserMessage(threadId, runId,
                    "Supervisor decided to route to: PLANNING (mandatory first stage)");
            Map<String, Object> updates = new HashMap<>();
            updates.put("nextAgent", "PLANNING");
            updates.put("retries", iteration + 1);
            return updates;
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder
                .append("You are the Orchestrator Supervisor for an Agentic Code Review & Remediation workflow.\n");
        promptBuilder.append(
                "Your job is to decide which of the specialized agents to invoke next, or if the process is COMPLETE.\n\n");

        // Include the remediation plan for informed routing
        String plan = state.getRemediationPlan();
        if (plan != null && !plan.isBlank()) {
            promptBuilder.append("=== REMEDIATION PLAN (follow this order) ===\n");
            promptBuilder.append(plan).append("\n");
            promptBuilder.append("=== END PLAN ===\n\n");
        }

        promptBuilder.append("Available Agents:\n");

        for (AgentStage stage : AgentStage.values()) {
            // Skip PLANNING since it has already run
            if (stage == AgentStage.PLANNING)
                continue;
            promptBuilder.append("- ").append(stage.name()).append(": ").append(stage.getStageName()).append("\n");
        }
        promptBuilder.append("- COMPLETE: If all necessary stages are done and the fix is fully verified.\n");
        promptBuilder.append("- FAIL: If the fix cannot be achieved after multiple attempts.\n\n");

        promptBuilder.append(
                "Rules:\n");
        promptBuilder.append("1. Follow the REMEDIATION PLAN order when deciding the next agent.\n");
        promptBuilder.append(
                "2. Reply ONLY with the exact name of the next agent (e.g. 'DISCOVERY', 'SECURITY', 'COMPLETE', or 'FAIL').\n");
        promptBuilder.append("3. Do not include any other text, reasoning, or markdown.\n");
        promptBuilder.append("4. Do NOT route to PLANNING again; it has already run.\n");

        SystemMessage systemMessage = new SystemMessage(promptBuilder.toString());

        // Pass the context of what has been done so far
        Map<String, String> outputs = state.getAgentOutputs();
        String contextStr = outputs.entrySet().stream()
                .map(e -> e.getKey() + " output: " + e.getValue())
                .collect(Collectors.joining("\n"));

        UserMessage userMessage = new UserMessage(
                "Current Context of Agent Execution:\n" + contextStr + "\nWhat is the next agent to invoke?");

        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, userMessage)
                .build();

        ChatResponse response = chatModel.chat(request);

        if (response.tokenUsage() != null && response.tokenUsage().totalTokenCount() != null) {
            String vulnId = state.getVulnerability() != null ? state.getVulnerability().getId() : null;
            llmService.recordTokens(llmService.getAgentModelName(), response.tokenUsage().totalTokenCount(),
                    state.getThreadId(), state.getRunId(), vulnId);
        }

        String aiText = response.aiMessage().text().trim();

        // Clean up AI response just in case
        String nextAgent = "FAIL";
        boolean matched = Arrays.stream(AgentStage.values())
                .filter(s -> s != AgentStage.PLANNING) // Don't allow routing back to PLANNING
                .anyMatch(s -> s.name().equals(aiText))
                || "COMPLETE".equals(aiText)
                || "FAIL".equals(aiText);

        if (matched) {
            nextAgent = aiText;
        } else {
            logger.warn("Supervisor returned invalid agent: {}. Defaulting to FAIL", aiText);
        }

        memoryService.addUserMessage(threadId, runId, "Supervisor decided to route to: " + nextAgent);

        Map<String, Object> updates = new HashMap<>();
        updates.put("nextAgent", nextAgent);
        updates.put("retries", iteration + 1);

        return updates;
    }
}
