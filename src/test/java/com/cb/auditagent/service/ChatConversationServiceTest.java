package com.cb.auditagent.service;

import com.cb.auditagent.config.LlmProperties;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatConversationServiceTest {

    private ChatModel mockChatModel;
    private StreamingChatModel mockStreamingChatModel;
    private ConversationMemoryService mockMemoryService;
    private LlmTokenAuditService mockTokenAuditService;
    private LlmProperties llmProperties;
    private ChatConversationService chatService;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        mockStreamingChatModel = mock(StreamingChatModel.class);
        mockMemoryService = mock(ConversationMemoryService.class);
        mockTokenAuditService = mock(LlmTokenAuditService.class);
        llmProperties = new LlmProperties();
        llmProperties.getOpenai().setModelName("test-chat-model");

        chatService = new ChatConversationService(
                mockChatModel,
                mockStreamingChatModel,
                mockMemoryService,
                mockTokenAuditService,
                llmProperties
        );
    }

    @Test
    @DisplayName("chat formats system prompt with active findings and command tags")
    void testChatFormatsSystemPromptWithActiveFindings() {
        Vulnerability vuln = new Vulnerability("VULN-000001", "src/Database.java", 42,
                "String query = \"SELECT * FROM users WHERE name = '\" + input + \"'\";",
                Severity.HIGH, "SQL_INJECTION", "Unsanitized input in query", "Java");

        ChatResponse mockResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("I will fix VULN-000001 for you. [TRIGGER_FIX:VULN-000001]"))
                .tokenUsage(new TokenUsage(50, 20, 70))
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        String answer = chatService.chat("thread-123", "/workspace/repo", "Fix the SQL injection issue", List.of(vuln));

        assertNotNull(answer);
        assertTrue(answer.contains("[TRIGGER_FIX:VULN-000001]"));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockChatModel).chat(captor.capture());
        ChatRequest capturedRequest = captor.getValue();
        List<ChatMessage> messages = capturedRequest.messages();
        assertEquals(2, messages.size());

        dev.langchain4j.data.message.SystemMessage sysMsg = (dev.langchain4j.data.message.SystemMessage) messages.get(0);
        String systemPrompt = sysMsg.text();
        assertTrue(systemPrompt.contains("COMMAND TAGS:"));
        assertTrue(systemPrompt.contains("[TRIGGER_SCAN]"));
        assertTrue(systemPrompt.contains("[TRIGGER_FIX:VULN-ID]"));
        assertTrue(systemPrompt.contains("VULN-000001"));
        assertTrue(systemPrompt.contains("SQL_INJECTION"));

        verify(mockTokenAuditService).recordTokens(eq("test-chat-model"), eq(70), eq("thread-123"), any(), any());
    }

    @Test
    @DisplayName("chat handles empty response from model gracefully")
    void testChatHandlesEmptyResponse() {
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(null);

        String answer = chatService.chat("thread-empty", "/workspace", "Hello", List.of());
        assertEquals("Agent error: received empty response from LLM.", answer);
    }

    @Test
    @DisplayName("chatStream streams tokens via StreamingChatModel")
    void testChatStreamWithStreamingModel() {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("Hello ");
            handler.onPartialResponse("world!");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hello world!"))
                    .tokenUsage(new TokenUsage(10, 5, 15))
                    .build());
            return null;
        }).when(mockStreamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        List<String> tokens = chatService.chatStream("thread-stream", "/workspace", "Stream test", List.of())
                .collectList()
                .block();

        assertNotNull(tokens);
        assertEquals(List.of("Hello ", "world!"), tokens);

        verify(mockTokenAuditService).recordTokens(eq("test-chat-model"), eq(15), eq("thread-stream"), any(), any());
    }

    @Test
    @DisplayName("chatStream falls back to ChatModel when StreamingChatModel is not provided")
    void testChatStreamFallbackToChatModel() {
        ChatConversationService syncChatService = new ChatConversationService(
                mockChatModel,
                null,
                mockMemoryService,
                mockTokenAuditService,
                llmProperties
        );

        ChatResponse mockResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Synchronous fallback response"))
                .tokenUsage(new TokenUsage(15, 10, 25))
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        List<String> tokens = syncChatService.chatStream("thread-sync", "/workspace", "Hello", List.of())
                .collectList()
                .block();

        assertNotNull(tokens);
        assertEquals(List.of("Synchronous fallback response"), tokens);

        verify(mockTokenAuditService).recordTokens(eq("test-chat-model"), eq(25), eq("thread-sync"), any(), any());
    }

    @Test
    @DisplayName("matchFindingForIssue resolves explicit VULN ID via regex without calling LLM")
    void testMatchFindingForIssueRegexMatch() {
        Vulnerability v1 = new Vulnerability("VULN-000001", "A.java", 10, "code", Severity.HIGH, "SQLI", "desc", "java");
        Vulnerability v2 = new Vulnerability("VULN-000002", "B.java", 20, "code", Severity.MEDIUM, "XSS", "desc", "java");

        Optional<String> matched = chatService.matchFindingForIssue(
                "Security Vulnerability in Database",
                "Please remediate vuln-000002 before release",
                List.of(v1, v2)
        );

        assertTrue(matched.isPresent());
        assertEquals("VULN-000002", matched.get());
        verifyNoInteractions(mockChatModel);
    }

    @Test
    @DisplayName("matchFindingForIssue falls back to LangChain4j semantic matching when no explicit ID present")
    void testMatchFindingForIssueSemanticMatch() {
        Vulnerability v1 = new Vulnerability("VULN-000001", "A.java", 10, "code", Severity.HIGH, "SQLI", "desc", "java");

        ChatResponse mockResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Matched to finding [MATCH:VULN-000001] based on SQL injection details."))
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        Optional<String> matched = chatService.matchFindingForIssue(
                "SQL Injection reported by pentest",
                "User input is concatenated into query string",
                List.of(v1)
        );

        assertTrue(matched.isPresent());
        assertEquals("VULN-000001", matched.get());
        verify(mockChatModel).chat(any(ChatRequest.class));
    }
}
