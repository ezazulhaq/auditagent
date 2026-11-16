package com.cb.auditagent.graph.node;

import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
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
import com.cb.auditagent.graph.agent.StageMilestone;
import com.cb.auditagent.graph.agent.SupervisorDecision;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SupervisorNode implements NodeAction<MultiAgentState> {
    private static final Logger logger = LoggerFactory.getLogger(SupervisorNode.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        int hops = state.getHops();

        // Check supervisor hop limit (Phase 4: max-supervisor-hops decoupled from stage retries)
        if (hops >= config.getMaxSupervisorHops()) {
            logger.warn("Supervisor hit max supervisor hops: {} (limit: {})", hops, config.getMaxSupervisorHops());
            return Map.of("nextAgent", "FAIL");
        }

        // Check if any stage has repeatedly failed and exceeded maxStageRetries
        Map<String, Integer> stageRetries = state.getStageRetries();
        boolean stageRetryExceeded = stageRetries.values().stream()
                .anyMatch(retries -> retries >= config.getMaxStageRetries());
        if (stageRetryExceeded) {
            logger.warn("Supervisor hit max stage retries (limit: {}) across stages: {}",
                    config.getMaxStageRetries(), stageRetries);
            return Map.of("nextAgent", "FAIL");
        }

        // Enforce PLANNING as the mandatory first stage
        if (!state.isPlanGenerated()) {
            logger.info("Supervisor: No plan generated yet. Routing to PLANNING stage.");
            memoryService.addUserMessage(threadId, runId,
                    "Supervisor decided to route to: PLANNING (mandatory first stage)");
            Map<String, Object> updates = new HashMap<>();
            updates.put("nextAgent", "PLANNING");
            updates.put("hops", hops + 1);
            updates.put("retries", hops + 1);
            updates.put("supervisorReasoning", "Mandatory first stage");
            return updates;
        }

        // Fast-path: Route with TypeSafe Jev Model if enabled
        String jevDecision = routeWithJev(state);
        if (jevDecision != null) {
            memoryService.addUserMessage(threadId, runId, "Supervisor decided to route to: " + jevDecision);
            Map<String, Object> updates = new HashMap<>();
            updates.put("nextAgent", jevDecision);
            updates.put("hops", hops + 1);
            updates.put("retries", hops + 1);
            updates.put("supervisorReasoning", "Selected via Jev fast-path routing");
            return updates;
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are the Orchestrator Supervisor for an Agentic Code Review & Remediation workflow.\n");
        promptBuilder.append("Your job is to decide which of the specialized agents to invoke next, or if the process is COMPLETE.\n\n");

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

        boolean structured = config.isStructuredSupervisor();
        if (structured) {
            promptBuilder.append("Rules:\n");
            promptBuilder.append("1. Follow the REMEDIATION PLAN order when deciding the next agent.\n");
            promptBuilder.append("2. Reply ONLY with a valid JSON object adhering to this schema:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"nextAgent\": \"<AGENT_NAME | COMPLETE | FAIL>\",\n");
            promptBuilder.append("  \"reasoning\": \"<concise explanation for selecting this agent>\"\n");
            promptBuilder.append("}\n");
            promptBuilder.append("3. The \"nextAgent\" value MUST be one of the available agents listed above, \"COMPLETE\", or \"FAIL\".\n");
            promptBuilder.append("4. Do NOT route to PLANNING again; it has already run.\n");
            promptBuilder.append("5. Do not include markdown code block formatting or additional commentary outside the JSON object.\n");
        } else {
            promptBuilder.append("Rules:\n");
            promptBuilder.append("1. Follow the REMEDIATION PLAN order when deciding the next agent.\n");
            promptBuilder.append("2. Reply ONLY with the exact name of the next agent (e.g. 'DISCOVERY', 'SECURITY', 'COMPLETE', or 'FAIL').\n");
            promptBuilder.append("3. Do not include any other text, reasoning, or markdown.\n");
            promptBuilder.append("4. Do NOT route to PLANNING again; it has already run.\n");
        }

        SystemMessage systemMessage = new SystemMessage(promptBuilder.toString());

        // Build compact execution context using StageMilestones (O(N)) instead of raw transcript dumps
        String contextStr = buildExecutionContext(state);
        UserMessage userMessage = new UserMessage(
                "Current Context of Agent Execution:\n" + contextStr + "\nWhat is the next agent to invoke?");

        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(systemMessage, userMessage);

        if (structured) {
            requestBuilder.responseFormat(ResponseFormat.JSON);
        }

        ChatRequest request = requestBuilder.build();

        ChatModel activeModel = clientFactory != null ? clientFactory.getSupervisorModel(state.getUserId()) : this.chatModel;
        String modelName = clientFactory != null ? clientFactory.getSupervisorModelName(state.getUserId()) : llmService.getAgentModelName();
        
        ChatResponse response;
        try {
            response = activeModel.chat(request);
        } catch (Exception e) {
            // Graceful fallback without ResponseFormat.JSON if the active model/provider rejects JSON response_format
            if (structured) {
                logger.warn("Supervisor model rejected ResponseFormat.JSON ({}), falling back to unstructured request", e.getMessage());
                ChatRequest fallbackRequest = ChatRequest.builder().messages(systemMessage, userMessage).build();
                response = activeModel.chat(fallbackRequest);
            } else {
                throw e;
            }
        }

        if (response.tokenUsage() != null && response.tokenUsage().totalTokenCount() != null) {
            String vulnId = state.getVulnerability() != null ? state.getVulnerability().getId() : null;
            llmService.recordTokens(modelName, response.tokenUsage().totalTokenCount(),
                    state.getThreadId(), state.getRunId(), vulnId);
        }

        String aiText = response.aiMessage().text() != null ? response.aiMessage().text().trim() : "";

        // Parse structured response with resilient fuzzy normalization
        SupervisorDecision decision = parseSupervisorResponse(aiText);
        String nextAgent = decision.nextAgent();
        String reasoning = decision.reasoning();

        // Check if the chosen stage has already exceeded retry limits
        if (stageRetries.getOrDefault(nextAgent, 0) >= config.getMaxStageRetries()) {
            logger.warn("Supervisor selected stage '{}' which has already failed {} times (max: {}). Routing to FAIL.",
                    nextAgent, stageRetries.get(nextAgent), config.getMaxStageRetries());
            nextAgent = "FAIL";
        }

        String logMsg = "Supervisor decided to route to: " + nextAgent +
                (reasoning != null && !reasoning.isBlank() ? " (" + reasoning + ")" : "");
        memoryService.addUserMessage(threadId, runId, logMsg);

        Map<String, Object> updates = new HashMap<>();
        updates.put("nextAgent", nextAgent);
        updates.put("hops", hops + 1);
        updates.put("retries", hops + 1);
        updates.put("supervisorReasoning", reasoning);

        return updates;
    }

    public String buildExecutionContext(MultiAgentState state) {
        List<StageMilestone> milestones = state.getMilestones();
        if (milestones != null && !milestones.isEmpty()) {
            return milestones.stream()
                    .map(m -> String.format("- Stage: %s | Status: %s | Touched Files: %s | Summary: %s",
                            m.stage() != null ? m.stage().name() : "UNKNOWN",
                            m.success() ? "SUCCESS" : "FAILED",
                            m.touchedFiles() != null && !m.touchedFiles().isEmpty() ? String.join(", ", m.touchedFiles()) : "none",
                            truncateTokens(m.summary(), 150)))
                    .collect(Collectors.joining("\n"));
        }

        Map<String, String> outputs = state.getAgentOutputs();
        return outputs.entrySet().stream()
                .map(e -> e.getKey() + " output: " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    public SupervisorDecision parseSupervisorResponse(String rawAiText) {
        if (rawAiText == null || rawAiText.isBlank()) {
            return new SupervisorDecision("FAIL", "Empty AI response");
        }

        String trimmed = rawAiText.trim();

        // 1. Try extracting and parsing JSON
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String jsonSub = trimmed.substring(firstBrace, lastBrace + 1);
            try {
                SupervisorDecision decision = objectMapper.readValue(jsonSub, SupervisorDecision.class);
                String normalized = normalizeAgentEnum(decision.nextAgent());
                if (normalized != null) {
                    return new SupervisorDecision(normalized, decision.reasoning());
                }
            } catch (Exception e) {
                logger.debug("Failed to deserialize JSON to SupervisorDecision: {}", e.getMessage());
            }
        }

        // 2. Fall back to fuzzy extraction from text
        String normalized = normalizeAgentEnum(trimmed);
        if (normalized != null) {
            return new SupervisorDecision(normalized, "Extracted from non-JSON response");
        }

        logger.warn("Supervisor returned unparseable agent: '{}'. Defaulting to FAIL", rawAiText);
        return new SupervisorDecision("FAIL", "Unrecognized agent in: " + rawAiText);
    }

    public String normalizeAgentEnum(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        // Clean surrounding markdown formatting, punctuation, quotes, brackets
        String cleaned = candidate.trim()
                .replaceAll("^[`*\"'#\\[({\\s]+", "")
                .replaceAll("[`*\"'#\\])}\\s.,:;]+$", "")
                .trim();

        // Standardize separators: dashes and spaces to underscores
        String normalized = cleaned.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if ("COMPLETE".equals(normalized) || "FAIL".equals(normalized)) {
            return normalized;
        }

        for (AgentStage stage : AgentStage.values()) {
            if (stage == AgentStage.PLANNING) {
                continue;
            }
            if (stage.name().equals(normalized)) {
                return stage.name();
            }
        }

        // Check word boundaries for COMPLETE or FAIL in raw text
        Pattern completePattern = Pattern.compile("\\bCOMPLETE\\b", Pattern.CASE_INSENSITIVE);
        if (completePattern.matcher(candidate).find()) {
            return "COMPLETE";
        }

        Pattern failPattern = Pattern.compile("\\bFAIL(?:URE)?\\b", Pattern.CASE_INSENSITIVE);
        if (failPattern.matcher(candidate).find()) {
            return "FAIL";
        }

        // Check word boundaries for individual stages
        for (AgentStage stage : AgentStage.values()) {
            if (stage == AgentStage.PLANNING) {
                continue;
            }
            String regex = "\\b" + stage.name().replace("_", "[_\\-\\s]") + "\\b";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(candidate).find()) {
                return stage.name();
            }
        }

        return null;
    }

    private String truncateTokens(String text, int maxTokens) {
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

            String contextStr = buildExecutionContext(state);
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
