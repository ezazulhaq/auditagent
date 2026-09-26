package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.MemoryMessageRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DuckDB-backed conversation memory. DuckDB, not the process heap, owns
 * history.
 */
@Service
public class ConversationMemoryService {
    private static final Logger logger = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final DatabaseService databaseService;
    private final MemoryConfig memoryConfig;
    private final MemoryRedactor redactor;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;

    public ConversationMemoryService(DatabaseService databaseService, MemoryConfig memoryConfig,
            MemoryRedactor redactor,
            ObjectMapper objectMapper,
            @Lazy ChatModel chatModel) {
        this.databaseService = databaseService;
        this.memoryConfig = memoryConfig;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    public void initConversation(String threadId, String systemPrompt) {
        initConversation(threadId, null, systemPrompt);
    }

    public void initConversation(String threadId, String runId, String systemPrompt) {
        databaseService.ensureMemoryThread(threadId, "legacy-local", "");
        if (databaseService.getMemoryMessages(threadId, runId, false).isEmpty()) {
            persist(threadId, runId, "SYSTEM", "SYSTEM", redactor.chat(systemPrompt), null);
        }
    }

    public void addUserMessage(String threadId, String content) {
        addUserMessage(threadId, null, content);
    }

    public void addUserMessage(String threadId, String runId, String content) {
        persist(threadId, runId, "USER", "TEXT", redactor.chat(content), null);
    }

    public void addAiMessage(String threadId, AiMessage message) {
        addAiMessage(threadId, null, message);
    }

    public void addAiMessage(String threadId, String runId, AiMessage message) {
        try {
            if (message.hasToolExecutionRequests()) {
                List<Map<String, String>> requests = message.toolExecutionRequests().stream().map(request -> Map.of(
                        "id", Optional.ofNullable(request.id()).orElse(""),
                        "name", Optional.ofNullable(request.name()).orElse(""),
                        "arguments", Optional.ofNullable(request.arguments()).orElse("{}"))).toList();
                persist(threadId, runId, "AI", "TOOL_REQUEST", redactor.chat(message.text()),
                        objectMapper.writeValueAsString(requests));
            } else {
                persist(threadId, runId, "AI", "TEXT", redactor.chat(message.text()), null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist assistant memory", e);
        }
    }

    public void addToolResults(String threadId, List<ToolExecutionRequest> requests, List<String> results) {
        addToolResults(threadId, null, requests, results);
    }

    public void addToolResults(String threadId, String runId, List<ToolExecutionRequest> requests,
            List<String> results) {
        int maxChars = memoryConfig.getMaxToolOutputChars();
        for (int i = 0; i < requests.size(); i++) {
            ToolExecutionRequest request = requests.get(i);
            try {
                String rawResult = i < results.size() ? results.get(i) : "ERROR: No result available";

                // Aggressively truncate large tool outputs before redaction
                String truncatedResult = truncateToolOutput(rawResult, maxChars);

                Map<String, String> metadata = Map.of(
                        "id", Optional.ofNullable(request.id()).orElse(""),
                        "name", Optional.ofNullable(request.name()).orElse(""),
                        "arguments", Optional.ofNullable(request.arguments()).orElse("{}"));
                persist(threadId, runId, "TOOL", "TOOL_RESULT",
                        redactor.tool(truncatedResult),
                        objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                throw new IllegalStateException("Could not persist tool memory", e);
            }
        }
    }

    public void addChatMessage(String threadId, String role, String content) {
        addChatMessage(threadId, role, content, "");
    }

    public void addChatMessage(String threadId, String role, String content, String repoPath) {
        databaseService.ensureMemoryThread(threadId, "legacy-local", repoPath != null ? repoPath : "");
        persist(threadId, null, role.toUpperCase(Locale.ROOT), "TEXT", redactor.chat(content), null);
    }

    public List<ChatMessage> getHistory(String threadId) {
        return getHistory(threadId, null);
    }

    public List<ChatMessage> getHistory(String threadId, String runId) {
        List<MemoryMessageRecord> records = databaseService.getMemoryMessages(threadId, runId, false);
        if (records.isEmpty())
            return List.of();
        List<List<MemoryMessageRecord>> turns = groupCompleteTurns(records);
        int budget = memoryConfig.getContextTokenBudget();
        List<List<MemoryMessageRecord>> selected = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < Math.min(2, turns.size()); i++) {
            selected.add(turns.get(i));
            used += estimateTokens(turns.get(i));
        }
        Deque<List<MemoryMessageRecord>> recent = new ArrayDeque<>();
        List<List<MemoryMessageRecord>> droppedTurns = new ArrayList<>();
        for (int i = turns.size() - 1; i >= 2; i--) {
            int cost = estimateTokens(turns.get(i));
            if (used + cost > budget) {
                // Collect dropped turns for summarization
                for (int j = 2; j <= i; j++) {
                    droppedTurns.add(turns.get(j));
                }
                break;
            }
            recent.addFirst(turns.get(i));
            used += cost;
        }
        boolean trimmed = selected.size() + recent.size() < turns.size();
        selected.addAll(recent);

        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            if (trimmed && i == Math.min(2, selected.size())) {
                // Generate compressed summary of dropped turns
                String summary = summarizeDroppedTurns(droppedTurns);
                history.add(new SystemMessage(summary));
            }
            for (MemoryMessageRecord record : selected.get(i))
                history.add(toChatMessage(record));
        }
        return history;
    }

    private List<List<MemoryMessageRecord>> groupCompleteTurns(List<MemoryMessageRecord> records) {
        List<List<MemoryMessageRecord>> turns = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            MemoryMessageRecord record = records.get(i);
            List<MemoryMessageRecord> turn = new ArrayList<>();
            turn.add(record);
            if ("TOOL_REQUEST".equals(record.messageType())) {
                while (i + 1 < records.size() && "TOOL_RESULT".equals(records.get(i + 1).messageType())) {
                    turn.add(records.get(++i));
                }
            }
            turns.add(turn);
        }
        return turns;
    }

    private ChatMessage toChatMessage(MemoryMessageRecord record) {
        return switch (record.role()) {
            case "SYSTEM" -> new SystemMessage(record.content());
            case "USER" -> new UserMessage(record.content());
            case "AI" -> toAiMessage(record);
            case "TOOL" -> toToolMessage(record);
            default -> new UserMessage(record.content());
        };
    }

    private AiMessage toAiMessage(MemoryMessageRecord record) {
        if (!"TOOL_REQUEST".equals(record.messageType()) || record.metadataJson() == null) {
            return AiMessage.from(Optional.ofNullable(record.content()).orElse(""));
        }
        try {
            List<Map<String, String>> values = objectMapper.readValue(record.metadataJson(), new TypeReference<>() {
            });
            List<ToolExecutionRequest> requests = values.stream().map(this::toToolRequest).toList();
            return AiMessage.from(requests);
        } catch (Exception e) {
            logger.warn("Could not reconstruct tool request memory {}", record.messageId(), e);
            return AiMessage.from(Optional.ofNullable(record.content()).orElse(""));
        }
    }

    private ToolExecutionResultMessage toToolMessage(MemoryMessageRecord record) {
        try {
            Map<String, String> value = objectMapper.readValue(record.metadataJson(), new TypeReference<>() {
            });
            return ToolExecutionResultMessage.from(toToolRequest(value), record.content());
        } catch (Exception e) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(record.messageId()).name("unknown_tool").arguments("{}").build();
            return ToolExecutionResultMessage.from(request, record.content());
        }
    }

    private ToolExecutionRequest toToolRequest(Map<String, String> value) {
        return ToolExecutionRequest.builder().id(value.get("id")).name(value.get("name"))
                .arguments(value.getOrDefault("arguments", "{}")).build();
    }

    private void persist(String threadId, String runId, String role, String type, String content, String metadata) {
        databaseService.appendMemoryMessage(threadId, runId, role, type, content, metadata);
    }

    private int estimateTokens(List<MemoryMessageRecord> records) {
        return records.stream().mapToInt(r -> Math.max(1, Optional.ofNullable(r.content()).orElse("").length() / 4))
                .sum();
    }

    public int getHistorySize(String threadId) {
        return databaseService.getMemoryMessages(threadId).size();
    }

    /**
     * Lifecycle cleanup now clears only transient caches; persisted history is
     * retained.
     */
    public void clearHistory(String threadId) {
        logger.debug("Durable memory retained for thread {}", threadId);
    }

    public void forgetHistory(String threadId) {
        databaseService.deleteMemoryThread(threadId);
    }

    public AiMessage getLastAiMessage(String threadId) {
        List<MemoryMessageRecord> records = databaseService.getMemoryMessages(threadId);
        for (int i = records.size() - 1; i >= 0; i--) {
            if ("AI".equals(records.get(i).role()))
                return toAiMessage(records.get(i));
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Context compression helpers
    // -----------------------------------------------------------------------

    /**
     * Generates a compressed summary of dropped conversation turns using an LLM
     * call.
     * Falls back to a static notice if summarization is disabled or the LLM call
     * fails.
     */
    private String summarizeDroppedTurns(List<List<MemoryMessageRecord>> droppedTurns) {
        if (droppedTurns.isEmpty()) {
            return "[Context Summary] No prior turns to summarize.";
        }

        if (!memoryConfig.isSummarizationEnabled() || chatModel == null) {
            return "[Context Summary] " + droppedTurns.size()
                    + " older conversation turns were compacted from durable memory.";
        }

        try {
            // Build a condensed representation of dropped turns
            StringBuilder droppedContent = new StringBuilder();
            int charBudget = 3000; // Cap input to summarizer to control cost
            int charsUsed = 0;

            for (List<MemoryMessageRecord> turn : droppedTurns) {
                for (MemoryMessageRecord record : turn) {
                    String line = record.role() + ": "
                            + Optional.ofNullable(record.content()).orElse("").substring(0,
                                    Math.min(Optional.ofNullable(record.content()).orElse("").length(), 200));
                    if (charsUsed + line.length() > charBudget)
                        break;
                    droppedContent.append(line).append("\n");
                    charsUsed += line.length();
                }
                if (charsUsed >= charBudget)
                    break;
            }

            SystemMessage systemMsg = new SystemMessage(
                    "You are a conversation summarizer. Compress the following agent conversation turns "
                            + "into a concise 3-5 sentence summary. Focus on: what tools were called, what files "
                            + "were read or modified, what the agent discovered, and any errors or decisions made. "
                            + "Do NOT include code snippets. Be terse.");
            UserMessage userMsg = new UserMessage(droppedContent.toString());

            ChatRequest request = ChatRequest.builder()
                    .messages(systemMsg, userMsg)
                    .build();
            ChatResponse response = chatModel.chat(request);

            String summary = response.aiMessage().text().trim();
            logger.info("Generated context summary for {} dropped turns ({} chars -> {} chars)",
                    droppedTurns.size(), charsUsed, summary.length());

            return "[Context Summary] " + summary;

        } catch (Exception e) {
            logger.warn("Failed to generate context summary, falling back to static notice", e);
            return "[Context Summary] " + droppedTurns.size()
                    + " older conversation turns were compacted from durable memory. "
                    + "Summarization failed: " + e.getMessage();
        }
    }

    /**
     * Aggressively truncates tool output, preserving the first and last portions
     * to keep both the header/status and the tail of the output visible.
     */
    private String truncateToolOutput(String output, int maxChars) {
        if (output == null || output.length() <= maxChars) {
            return output;
        }

        // Keep 70% from the start (status, headers) and 30% from the end (summary, exit
        // codes)
        int headSize = (int) (maxChars * 0.7);
        int tailSize = maxChars - headSize - 40; // 40 chars for the separator message
        if (tailSize < 0)
            tailSize = 0;

        String head = output.substring(0, headSize);
        String tail = tailSize > 0 ? output.substring(output.length() - tailSize) : "";
        int droppedLines = output.substring(headSize, output.length() - (tailSize > 0 ? tailSize : 0))
                .split("\n").length;

        return head + "\n... [" + droppedLines + " lines truncated] ...\n" + tail;
    }
}
