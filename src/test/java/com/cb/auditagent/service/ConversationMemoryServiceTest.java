package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationMemoryServiceTest {

    @MockitoBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @MockitoBean
    private dev.langchain4j.model.chat.ChatModel agentModel;

    @Autowired
    private MemoryConfig memoryConfig;

    @Autowired
    private ConversationMemoryService service;

    @BeforeEach
    void setUp() {
        memoryConfig.setFtsEnabled(false);
    }

    @Test
    void conversationSurvivesServiceRecreation() {
        service.initConversation("thread-1", "System prompt");
        service.addUserMessage("thread-1", "Fix VULN-001");
        service.addAiMessage("thread-1", AiMessage.from("I will inspect it."));

        List<ChatMessage> history = service.getHistory("thread-1");

        assertEquals(3, history.size());
        assertInstanceOf(SystemMessage.class, history.get(0));
        assertInstanceOf(UserMessage.class, history.get(1));
        assertEquals("I will inspect it.", service.getLastAiMessage("thread-1").text());
    }

    @Test
    void toolRequestAndResultsRemainPaired() {
        service.initConversation("thread-tools", "System");
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1").name("read_file").arguments("{}").build();
        service.addAiMessage("thread-tools", AiMessage.from(List.of(request)));
        service.addToolResults("thread-tools", List.of(request), List.of("file contents"));

        List<ChatMessage> history = service.getHistory("thread-tools");
        assertEquals(3, history.size());
        assertTrue(((AiMessage) history.get(1)).hasToolExecutionRequests());
        assertInstanceOf(ToolExecutionResultMessage.class, history.get(2));
    }

    @Test
    void secretsAreRedactedAndMessagesAreCapped() {
        memoryConfig.setMaxChatChars(100);
        service.addChatMessage("thread-secret", "USER",
                "password = super-secret AWS_ACCESS_KEY_ID=AKIA1234567890ABCDEF " + "x".repeat(200));

        UserMessage stored = (UserMessage) service.getHistory("thread-secret").get(0);
        assertFalse(stored.singleText().contains("super-secret"));
        assertFalse(stored.singleText().contains("AKIA1234567890ABCDEF"));
        assertTrue(stored.singleText().length() <= 100);
    }

    @Test
    void contextBudgetCompactsOnlyCompleteTurns() {
        memoryConfig.setContextTokenBudget(20);
        service.initConversation("thread-budget", "System prompt");
        service.addUserMessage("thread-budget", "Task prompt");
        for (int i = 0; i < 10; i++) {
            service.addUserMessage("thread-budget", "A long historical message number " + i);
        }

        List<ChatMessage> history = service.getHistory("thread-budget");
        assertInstanceOf(SystemMessage.class, history.get(0));
        assertEquals("Task prompt", ((UserMessage) history.get(1)).singleText());
        assertTrue(history.stream()
                .anyMatch(message -> message instanceof SystemMessage sm
                        && sm.text().contains("Context Summary")));
    }

    @Test
    void clearRetainsDurableHistoryAndForgetDeletesIt() {
        service.initConversation("thread-forget", "System");
        service.addUserMessage("thread-forget", "Hello");

        service.clearHistory("thread-forget");
        assertEquals(2, service.getHistorySize("thread-forget"));

        service.forgetHistory("thread-forget");
        assertTrue(service.getHistory("thread-forget").isEmpty());
    }
}
