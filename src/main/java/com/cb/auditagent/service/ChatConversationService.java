package com.cb.auditagent.service;

import com.cb.auditagent.config.LlmProperties;
import com.cb.auditagent.domain.Vulnerability;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service providing conversational AI assistance, vulnerability triage, and command dispatching
 * standardized on LangChain4j.
 * Extracted from LlmService during Phase 3 decomposition.
 */
@Service
@Primary
public class ChatConversationService implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatConversationService.class);

    private static final Pattern VULN_ID_PATTERN = Pattern.compile("VULN-[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATCH_TAG_PATTERN = Pattern.compile("\\[MATCH:(VULN-[A-Za-z0-9_-]+)\\]", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ConversationMemoryService memoryService;
    private final LlmTokenAuditService tokenAuditService;
    private final LlmProperties llmProperties;
    private UserLlmClientFactory clientFactory;

    public ChatConversationService(
            @Lazy ChatModel chatModel,
            @Autowired(required = false) @Lazy StreamingChatModel streamingChatModel,
            ConversationMemoryService memoryService,
            LlmTokenAuditService tokenAuditService,
            LlmProperties llmProperties) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.memoryService = memoryService;
        this.tokenAuditService = tokenAuditService;
        this.llmProperties = llmProperties;
    }

    @Autowired(required = false)
    public void setClientFactory(UserLlmClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String chat(String userMessage, List<Vulnerability> findings) {
        return chat(null, null, userMessage, findings);
    }

    @Override
    public String chat(String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        logger.info("Processing chat message with LangChain4j ({} characters)",
                userMessage == null ? 0 : userMessage.length());

        String conversationContext = resolveConversationContext(threadId, repoPath, userMessage);
        String systemInstructions = buildSystemInstructions(findings, conversationContext);

        ChatModel activeModel = resolveChatModel(null);
        String activeModelName = resolveModelName(null);

        try {
            SystemMessage systemMsg = SystemMessage.from(systemInstructions);
            UserMessage userMsg = UserMessage.from(Objects.toString(userMessage, ""));
            ChatRequest request = ChatRequest.builder()
                    .messages(systemMsg, userMsg)
                    .build();

            ChatResponse response = activeModel.chat(request);
            if (response != null && response.tokenUsage() != null
                    && response.tokenUsage().totalTokenCount() != null) {
                tokenAuditService.recordTokens(activeModelName, response.tokenUsage().totalTokenCount(),
                        threadId, null, null);
            }

            if (response == null || response.aiMessage() == null) {
                return "Agent error: received empty response from LLM.";
            }

            String answer = response.aiMessage().text();
            persistChatAnswer(threadId, answer, repoPath);
            return answer;
        } catch (Exception e) {
            LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(e);
            logger.error("Chat generation failed: {}", classified.userMessage());
            return "Error: " + classified.userMessage();
        }
    }

    @Override
    public Flux<String> chatStream(String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        return chatStream(null, threadId, repoPath, userMessage, findings);
    }

    @Override
    public Flux<String> chatStream(String userId, String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        logger.info("Processing chat message ({} characters) - Streaming via LangChain4j",
                userMessage == null ? 0 : userMessage.length());

        String conversationContext = resolveConversationContext(threadId, repoPath, userMessage);
        String systemInstructions = buildSystemInstructions(findings, conversationContext);

        ChatModel activeModel = resolveChatModel(userId);
        StreamingChatModel activeStreamingModel = resolveStreamingChatModel(userId);
        String activeModelName = resolveModelName(userId);

        SystemMessage systemMsg = SystemMessage.from(systemInstructions);
        UserMessage userMsg = UserMessage.from(Objects.toString(userMessage, ""));
        ChatRequest request = ChatRequest.builder()
                .messages(systemMsg, userMsg)
                .build();

        if (activeStreamingModel != null) {
            return Flux.<String>create(sink -> {
                StringBuilder fullResponse = new StringBuilder();
                try {
                    activeStreamingModel.chat(request, new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            if (partialResponse != null && !partialResponse.isEmpty()) {
                                fullResponse.append(partialResponse);
                                sink.next(partialResponse);
                            }
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            if (completeResponse != null && completeResponse.tokenUsage() != null
                                    && completeResponse.tokenUsage().totalTokenCount() != null
                                    && completeResponse.tokenUsage().totalTokenCount() > 0) {
                                tokenAuditService.recordTokens(activeModelName,
                                        completeResponse.tokenUsage().totalTokenCount(), threadId, null, null);
                            }
                            persistChatAnswer(threadId, fullResponse.toString().trim(), repoPath);
                            sink.complete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(error);
                            logger.error("Chat streaming failed with {}: {}", error.getClass().getSimpleName(), classified.userMessage());
                            String errorMsg = "Error: " + classified.userMessage();
                            persistChatAnswer(threadId, errorMsg, repoPath);
                            sink.next(errorMsg);
                            sink.complete();
                        }
                    });
                } catch (Exception e) {
                    LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(e);
                    logger.error("Chat stream initiation failed: {}", classified.userMessage());
                    String errorMsg = "Error: " + classified.userMessage();
                    persistChatAnswer(threadId, errorMsg, repoPath);
                    sink.next(errorMsg);
                    sink.complete();
                }
            });
        }

        // Fallback to synchronous model wrapped in Flux if streaming model is not configured
        return Flux.defer(() -> {
            try {
                ChatResponse response = activeModel.chat(request);
                if (response != null && response.tokenUsage() != null
                        && response.tokenUsage().totalTokenCount() != null
                        && response.tokenUsage().totalTokenCount() > 0) {
                    tokenAuditService.recordTokens(activeModelName,
                            response.tokenUsage().totalTokenCount(), threadId, null, null);
                }

                String answer = (response != null && response.aiMessage() != null)
                        ? response.aiMessage().text()
                        : "Agent error: received empty response from LLM.";
                persistChatAnswer(threadId, answer, repoPath);
                return Flux.just(answer);
            } catch (Exception e) {
                LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(e);
                logger.error("Chat response generation failed with {}: {}", e.getClass().getSimpleName(), classified.userMessage());
                String errorMsg = "Error: " + classified.userMessage();
                persistChatAnswer(threadId, errorMsg, repoPath);
                return Flux.just(errorMsg);
            }
        });
    }

    @Override
    public Optional<String> matchFindingForIssue(String issueTitle, String issueBody, List<Vulnerability> activeFindings) {
        if (activeFindings == null || activeFindings.isEmpty()) {
            return Optional.empty();
        }

        // Tier 1: Regex matching against finding ID format
        String combinedText = ((issueTitle != null ? issueTitle : "") + " " + (issueBody != null ? issueBody : "")).trim();
        Matcher matcher = VULN_ID_PATTERN.matcher(combinedText);
        while (matcher.find()) {
            String extracted = matcher.group().toUpperCase();
            for (Vulnerability v : activeFindings) {
                if (v.getId() != null && v.getId().equalsIgnoreCase(extracted)) {
                    return Optional.of(v.getId());
                }
            }
        }

        // Tier 2: LLM semantic triage matching against active findings
        if (chatModel == null) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder();
        for (Vulnerability v : activeFindings) {
            sb.append(String.format("- ID: %s | Rule: %s | Type: %s | File: %s | Line: %d | Description: %s\n",
                    v.getId(), v.getRuleId(), v.getVulnType(), v.getFilePath(), v.getLineNumber(), v.getDescription()));
        }

        String prompt = "You are an automated security triage assistant. A GitHub issue was reported:\n"
                + "Title: " + (issueTitle != null ? issueTitle : "") + "\n"
                + "Description: " + (issueBody != null ? issueBody : "") + "\n\n"
                + "Here are the active security findings detected in the repository:\n"
                + sb.toString() + "\n"
                + "Match the reported issue to the most relevant finding ID from the list.\n"
                + "If there is a clear match, respond ONLY with: [MATCH:<VULN_ID>] (e.g. [MATCH:VULN-000001]).\n"
                + "If none of the findings match the issue, respond ONLY with: [NO_MATCH].";

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build();
            ChatResponse response = chatModel.chat(request);
            String output = (response != null && response.aiMessage() != null)
                    ? response.aiMessage().text()
                    : "";

            Matcher matchResult = MATCH_TAG_PATTERN.matcher(output);
            if (matchResult.find()) {
                String matchedId = matchResult.group(1).toUpperCase();
                for (Vulnerability v : activeFindings) {
                    if (v.getId() != null && v.getId().equalsIgnoreCase(matchedId)) {
                        return Optional.of(v.getId());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("LLM issue finding matching failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    private String resolveConversationContext(String threadId, String repoPath, String userMessage) {
        if (threadId != null && !threadId.isBlank() && memoryService != null) {
            memoryService.addChatMessage(threadId, "USER", userMessage, repoPath);
            List<ChatMessage> history = new ArrayList<>(memoryService.getHistory(threadId));
            if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
                history.remove(history.size() - 1);
            }
            return history.stream().map(this::renderChatMessage)
                    .filter(value -> !value.isBlank()).collect(Collectors.joining("\n"));
        }
        return "";
    }

    private String buildSystemInstructions(List<Vulnerability> findings, String conversationContext) {
        StringBuilder findingsContext = new StringBuilder();
        if (findings == null || findings.isEmpty()) {
            findingsContext.append(
                    "No active vulnerabilities found in the project currently. The user should run a scan first.");
        } else {
            findingsContext.append("The following vulnerabilities are currently active in the project:\n");
            for (Vulnerability v : findings) {
                findingsContext.append(String.format(
                        "- ID: %s | Severity: %s | Type: %s | File: %s | Line: %d | Status: %s | Description: %s\n",
                        v.getId(), v.getSeverity(), v.getVulnType(), v.getFilePath(), v.getLineNumber(), v.getStatus(),
                        v.getDescription()));
            }
        }

        return "You are the Intelligent Audit and Compliance Agent. You help developers find and remediate security vulnerabilities in their codebase.\n"
                + "You are given a list of vulnerabilities found in the project. "
                + "If the user wants to scan, fix, or inspect a vulnerability, you should guide them and insert specific command tags in your response.\n\n"
                + "COMMAND TAGS:\n"
                + "- To initiate a scan: [TRIGGER_SCAN]\n"
                + "- To initiate a fix/analysis for a vulnerability ID: [TRIGGER_FIX:VULN-ID] (replace VULN-ID with the exact ID from the findings list, e.g. VULN-000001). You MUST match the user's intent to one of the active findings in the list.\n\n"
                + "ACTIVE FINDINGS:\n"
                + findingsContext.toString() + "\n\n"
                + "RECENT CONVERSATION:\n" + conversationContext + "\n\n"
                + "If the user asks to fix a vulnerability (e.g. \"fix SQL injection\" or \"fix VULN-123456\" or \"fix the issue in Database.java\"), check the findings list. Find the one that matches best. If you find a match, say something like: \"I am going to analyze and generate a patch for VULN-123456 (SQL Injection) in Database.java. [TRIGGER_FIX:VULN-123456]\"\n"
                + "Always output the exact tag [TRIGGER_FIX:VULN-XXXXXX] at the end or within your response. If there are multiple potential matches, list them and ask the user to clarify which ID they want to fix.\n"
                + "If the user asks to scan the project/repo, include [TRIGGER_SCAN].\n"
                + "If you cannot identify which vulnerability they want to fix or if they ask a general question, explain the vulnerabilities, ask for clarification, or discuss security best practices. Be helpful, concise, and professional.";
    }

    private void persistChatAnswer(String threadId, String answer, String repoPath) {
        if (threadId != null && !threadId.isBlank() && memoryService != null) {
            Thread.startVirtualThread(() -> {
                try {
                    memoryService.addChatMessage(threadId, "AI", answer, repoPath);
                } catch (Exception e) {
                    logger.warn("Failed to persist assistant chat message for thread {}", threadId, e);
                }
            });
        }
    }

    private String renderChatMessage(ChatMessage message) {
        if (message instanceof SystemMessage system)
            return "SYSTEM: " + system.text();
        if (message instanceof UserMessage user)
            return "USER: " + user.singleText();
        if (message instanceof AiMessage ai)
            return "ASSISTANT: " + Optional.ofNullable(ai.text()).orElse("");
        if (message instanceof ToolExecutionResultMessage tool)
            return "TOOL: " + tool.text();
        return "";
    }

    private ChatModel resolveChatModel(String userId) {
        if (clientFactory != null && userId != null) {
            ChatModel userModel = clientFactory.getAgentModel(userId);
            if (userModel != null) {
                return userModel;
            }
        }
        return this.chatModel;
    }

    private StreamingChatModel resolveStreamingChatModel(String userId) {
        if (clientFactory != null && userId != null) {
            StreamingChatModel userModel = clientFactory.getStreamingChatModel(userId);
            if (userModel != null) {
                return userModel;
            }
        }
        return this.streamingChatModel;
    }

    private String resolveModelName(String userId) {
        if (clientFactory != null && userId != null) {
            return clientFactory.getAgentModelName(userId);
        }
        if (llmProperties != null && llmProperties.getOpenai() != null
                && llmProperties.getOpenai().getModelName() != null) {
            return llmProperties.getOpenai().getModelName();
        }
        return "default-model";
    }
}
