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

import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevQuestion;
import com.cb.auditagent.client.jev.JevRequest;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import com.cb.auditagent.service.UserLlmConfigService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SupervisorNode implements NodeAction<MultiAgentState> {
    private static final Logger logger = LoggerFactory.getLogger(SupervisorNode.class);

    private final ChatModel chatModel;
    private final ConversationMemoryService memoryService;
    private final AgentConfig config;
    private final LlmService llmService;
    private final com.cb.auditagent.service.UserLlmClientFactory clientFactory;
    private final JevClient jevClient;
    private final JevProperties jevProperties;

    public SupervisorNode(
            ChatModel chatModel,
            ConversationMemoryService memoryService,
            AgentConfig config,
            @Lazy LlmService llmService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) @Lazy com.cb.auditagent.service.UserLlmClientFactory clientFactory) {
        this(chatModel, memoryService, config, llmService, clientFactory, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SupervisorNode(
            ChatModel chatModel,
            ConversationMemoryService memoryService,
            AgentConfig config,
            @Lazy LlmService llmService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) @Lazy com.cb.auditagent.service.UserLlmClientFactory clientFactory,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JevClient jevClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JevProperties jevProperties) {
        this.chatModel = chatModel;
        this.memoryService = memoryService;
        this.config = config;
        this.llmService = llmService;
        this.clientFactory = clientFactory;
        this.jevClient = jevClient;
        this.jevProperties = jevProperties;
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

        // Fast-path: Route with TypeSafe Jev Model if enabled
        String jevDecision = routeWithJev(state);
        if (jevDecision != null) {
            memoryService.addUserMessage(threadId, runId, "Supervisor decided to route to: " + jevDecision);
            Map<String, Object> updates = new HashMap<>();
            updates.put("nextAgent", jevDecision);
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

        ChatModel activeModel = clientFactory != null ? clientFactory.getSupervisorModel(state.getUserId()) : this.chatModel;
        String modelName = clientFactory != null ? clientFactory.getSupervisorModelName(state.getUserId()) : llmService.getAgentModelName();
        ChatResponse response = activeModel.chat(request);

        if (response.tokenUsage() != null && response.tokenUsage().totalTokenCount() != null) {
            String vulnId = state.getVulnerability() != null ? state.getVulnerability().getId() : null;
            llmService.recordTokens(modelName, response.tokenUsage().totalTokenCount(),
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

    private String routeWithJev(MultiAgentState state) {
        if (jevClient == null || !jevClient.isEnabled() || jevProperties == null || !jevProperties.getSupervisor().isEnabled()) {
            return null;
        }

        String userId = state.getUserId();
        if (clientFactory != null && userId != null && !clientFactory.isJevEnabled(userId)) {
            logger.info("Supervisor: Jev is disabled by user {} profile settings. Skipping fast-path routing.", userId);
            return null;
        }

        try {
            StringBuilder stateBuilder = new StringBuilder();
            stateBuilder.append("Workflow Context:\n");
            String plan = state.getRemediationPlan();
            if (plan != null && !plan.isBlank()) {
                stateBuilder.append("=== REMEDIATION PLAN (follow this order) ===\n").append(plan).append("\n=== END PLAN ===\n\n");
            }

            Map<String, String> outputs = state.getAgentOutputs();
            String contextStr = outputs.entrySet().stream()
                    .map(e -> e.getKey() + " output: " + e.getValue())
                    .collect(Collectors.joining("\n"));
            stateBuilder.append("Current Context of Agent Execution:\n").append(contextStr).append("\n");

            List<String> options = Arrays.stream(AgentStage.values())
                    .filter(s -> s != AgentStage.PLANNING)
                    .map(Enum::name)
                    .collect(Collectors.toList());
            options.add("COMPLETE");
            options.add("FAIL");

            Map<String, String> stageCriteria = new LinkedHashMap<>();
            for (String opt : options) {
                stageCriteria.put(opt, "Execute stage " + opt);
            }

            JevQuestion question = JevQuestion.choice(
                    "next_stage",
                    "Based on the remediation plan and execution history, which agent stage must execute next?",
                    stageCriteria
            );

            String modelName = jevProperties.getModelName();
            String apiKey = null;
            String baseUrl = null;
            if (clientFactory != null && userId != null) {
                String userModel = clientFactory.getJevModelName(userId);
                if (userModel != null && !userModel.isBlank()) {
                    modelName = userModel;
                }
                apiKey = clientFactory.getJevApiKey(userId);
                baseUrl = clientFactory.getJevBaseUrl(userId);
            }

            JevRequest request = new JevRequest(
                    modelName,
                    stateBuilder.toString(),
                    List.of(question)
            );

            JevResponse response = jevClient.execute(request, "supervisor_routing", apiKey, baseUrl);
            String selected = response.getAnswerValue("next_stage");
            if (selected != null && options.contains(selected)) {
                if (response.getUsage() != null && response.getUsage().getTotalTokens() != null) {
                    String vulnId = state.getVulnerability() != null ? state.getVulnerability().getId() : null;
                    llmService.recordTokens(jevProperties.getModelName(), response.getUsage().getTotalTokens(),
                            state.getThreadId(), state.getRunId(), vulnId);
                }
                logger.info("Supervisor: Jev selected next stage '{}' (confidence: {})", selected, response.getAnswerConfidence("next_stage"));
                return selected;
            } else {
                logger.warn("Supervisor: Jev returned unmapped stage '{}'", selected);
                return null;
            }
        } catch (Exception e) {
            logger.warn("Supervisor: Jev routing call failed: {}. Falling back to standard ChatModel.", e.getMessage());
            return null;
        }
    }
}
