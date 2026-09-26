package com.cb.auditagent.graph.node;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ModelCallNode implements NodeAction<RemediationState> {
    private static final Logger logger = LoggerFactory.getLogger(ModelCallNode.class);

    private final ChatModel agentModel;
    private final AgentConfig agentConfig;
    private final ConversationMemoryService memoryService;
    private final LlmService llmService;

    public ModelCallNode(ChatModel agentModel,
            AgentConfig agentConfig,
            ConversationMemoryService memoryService,
            @Lazy LlmService llmService) {
        this.agentModel = agentModel;
        this.agentConfig = agentConfig;
        this.memoryService = memoryService;
        this.llmService = llmService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) throws Exception {
        String runId = state.getRunId();
        String threadId = state.getThreadId();
        int iteration = state.getIteration() + 1; // Increment iteration

        logger.info("Executing ModelCallNode for run {} at iteration {}", runId, iteration);

        List<ToolSpecification> toolSpecs = llmService.buildToolSpecifications();
        List<String> allowedTools = state.getAllowedTools();
        if (allowedTools != null && !allowedTools.isEmpty()) {
            toolSpecs = toolSpecs.stream()
                    .filter(tool -> allowedTools.contains(tool.name()))
                    .toList();
        }

        List<ChatMessage> history = memoryService.getHistory(threadId, runId);

        ChatRequest request = ChatRequest.builder()
                .messages(history)
                .toolSpecifications(toolSpecs)
                .build();

        ChatResponse response = null;
        CompletableFuture<ChatResponse> llmFuture = CompletableFuture
                .supplyAsync(() -> agentModel.chat(request));

        try {
            response = llmFuture.get(agentConfig.getLlmCallTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            llmFuture.cancel(true);
            logger.error("LLM call timed out at iteration {}", iteration);

            memoryService.addUserMessage(threadId, runId,
                    "The previous LLM call timed out. Please provide a concise response and avoid unnecessary analysis.");

            Map<String, Object> updates = new HashMap<>();
            updates.put("iteration", iteration);
            updates.put("nextAction", "timeout");
            return updates;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                    && cause.getMessage().contains("text cannot be null")) {
                logger.warn("Caught LangChain4j empty text exception for run {} at iteration {}", runId, iteration);
                memoryService.addUserMessage(threadId, runId,
                        "Your previous response was completely empty. You must either provide text or call a tool.");
                Map<String, Object> updates = new HashMap<>();
                updates.put("iteration", iteration);
                updates.put("nextAction", "empty");
                return updates;
            }
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (response != null && response.tokenUsage() != null && response.tokenUsage().totalTokenCount() != null) {
            String vulnId = state.getVulnerability() != null ? state.getVulnerability().getId() : null;
            llmService.recordTokens(llmService.getAgentModelName(), response.tokenUsage().totalTokenCount(), threadId,
                    runId, vulnId);
        }

        if (response == null || response.aiMessage() == null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("iteration", iteration);
            updates.put("nextAction", "empty");
            return updates;
        }

        AiMessage aiMessage = response.aiMessage();
        memoryService.addAiMessage(threadId, runId, aiMessage);

        Map<String, Object> updates = new HashMap<>();
        updates.put("iteration", iteration);

        if (aiMessage.hasToolExecutionRequests()) {
            updates.put("nextAction", "tools");
            updates.put("finalResponse", null);
        } else {
            updates.put("nextAction", "verification_gate");
            String text = aiMessage.text();
            updates.put("finalResponse",
                    text != null && !text.isBlank() ? text.trim() : "Agent completed without a final response.");
        }

        return updates;
    }
}
