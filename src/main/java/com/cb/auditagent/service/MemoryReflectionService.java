package com.cb.auditagent.service;

import com.cb.auditagent.config.LlmProperties;
import com.cb.auditagent.domain.Vulnerability;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for distilling positive remediation patterns from diffs
 * and negative anti-patterns from failed remediation trajectories.
 * Extracted from LlmService during Phase 3 decomposition.
 */
@Service
public class MemoryReflectionService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryReflectionService.class);

    private final ChatModel chatModel;
    private final LlmTokenAuditService tokenAuditService;
    private final DatabaseService databaseService;
    private final MemoryRedactor memoryRedactor;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public MemoryReflectionService(
            @Lazy ChatModel chatModel,
            LlmTokenAuditService tokenAuditService,
            @Autowired(required = false) DatabaseService databaseService,
            @Autowired(required = false) MemoryRedactor memoryRedactor,
            LlmProperties llmProperties,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.tokenAuditService = tokenAuditService;
        this.databaseService = databaseService;
        this.memoryRedactor = memoryRedactor;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    public void summarizeDiffForGlobalPattern(Vulnerability vuln, String diff, String runId, String threadId) {
        if (vuln == null || diff == null || chatModel == null || databaseService == null) {
            return;
        }

        try {
            // Cap diff to avoid exceeding model context window
            String cappedDiff = diff.length() > 10_000 ? diff.substring(0, 10_000) + "\n... [truncated]" : diff;
            String promptText = "You are a senior security engineer. A developer has fixed a vulnerability in a pull request.\n"
                    + "Vulnerability Rule ID: " + vuln.getRuleId() + "\n"
                    + "Vulnerability Type: " + vuln.getVulnType() + "\n"
                    + "Write a concise, abstract positive remediation pattern (max 500 characters) explaining "
                    + "how to fix this type of vulnerability. Do NOT include any proprietary code, variable names, "
                    + "file paths, or class names. Focus purely on the security mechanism.\n\n"
                    + "Diff:\n" + cappedDiff;

            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(promptText))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String modelName = getModelName();
            if (response != null && response.tokenUsage() != null
                    && response.tokenUsage().totalTokenCount() != null) {
                tokenAuditService.recordTokens(modelName, response.tokenUsage().totalTokenCount(), threadId,
                        runId, vuln.getId());
            }

            String positivePattern = (response != null && response.aiMessage() != null)
                    ? response.aiMessage().text()
                    : "";
            final String redactedPositive = memoryRedactor != null
                    ? memoryRedactor.redactAndCap(positivePattern, 2000)
                    : positivePattern;

            databaseService.withTransaction(conn -> {
                try {
                    databaseService.saveGlobalPatternTx(conn, vuln.getRuleId(), redactedPositive, null, runId);
                } catch (Exception e) {
                    logger.warn("Failed to save positive pattern for rule {}", vuln.getRuleId(), e);
                }
            });
            logger.debug("Saved positive reflection pattern for rule {}", vuln.getRuleId());
        } catch (Exception e) {
            logger.warn("Positive reflection failed for rule {}", vuln.getRuleId(), e);
        }
    }

    public void reflectOnFailure(Vulnerability vuln, String runId, String threadId, List<Map<String, Object>> trajectory) {
        if (vuln == null || chatModel == null || databaseService == null) {
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                int lastN = Math.min(5, trajectory != null ? trajectory.size() : 0);
                List<Map<String, Object>> lastSteps = (trajectory != null && !trajectory.isEmpty())
                        ? trajectory.subList(trajectory.size() - lastN, trajectory.size())
                        : List.of();
                String trajectoryJson = objectMapper.writeValueAsString(lastSteps);
                if (trajectoryJson.length() > 8000) {
                    trajectoryJson = trajectoryJson.substring(0, 8000) + "\n... [truncated]";
                }
                String promptText = "You are a senior security engineer. An automated remediation agent tried to fix "
                        + "a vulnerability but failed after maximum iterations.\n"
                        + "Vulnerability Rule ID: " + vuln.getRuleId() + "\n"
                        + "Vulnerability Type: " + vuln.getVulnType() + "\n"
                        + "Write a concise negative anti-pattern warning (max 500 characters) for future agents "
                        + "explaining exactly what NOT to do for this rule ID. Be specific. "
                        + "Do NOT include any proprietary code, variable names, file paths, or class names.\n\n"
                        + "Last 5 iterations of the failed trajectory:\n" + trajectoryJson;

                ChatRequest request = ChatRequest.builder()
                        .messages(UserMessage.from(promptText))
                        .build();

                ChatResponse response = chatModel.chat(request);
                String modelName = getModelName();
                if (response != null && response.tokenUsage() != null
                        && response.tokenUsage().totalTokenCount() != null) {
                    tokenAuditService.recordTokens(modelName, response.tokenUsage().totalTokenCount(),
                            threadId, runId, vuln.getId());
                }

                String negativePattern = (response != null && response.aiMessage() != null)
                        ? response.aiMessage().text()
                        : "";
                final String redactedNegative = memoryRedactor != null
                        ? memoryRedactor.redactAndCap(negativePattern, 2000)
                        : negativePattern;

                databaseService.withTransaction(conn -> {
                    try {
                        databaseService.saveGlobalPatternTx(conn, vuln.getRuleId(), null, redactedNegative, runId);
                    } catch (Exception e) {
                        logger.warn("Failed to save negative pattern for rule {}", vuln.getRuleId(), e);
                    }
                });
                logger.debug("Saved negative reflection pattern for rule {}", vuln.getRuleId());
            } catch (Exception e) {
                logger.warn("Failure reflection failed for rule {}", vuln.getRuleId(), e);
            }
        });
    }

    private String getModelName() {
        if (llmProperties != null && llmProperties.getOpenai() != null
                && llmProperties.getOpenai().getModelName() != null) {
            return llmProperties.getOpenai().getModelName();
        }
        return "default-chat-model";
    }
}
