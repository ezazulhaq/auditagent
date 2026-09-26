package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.MemoryRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryServiceTest {
    @TempDir
    Path tempDir;

    private MemoryConfig memoryConfig;
    private ObjectMapper objectMapper;
    private ConversationMemoryService service;
    private Path dbPath;

    @BeforeEach
    void setUp() {
        memoryConfig = new MemoryConfig();
        memoryConfig.setFtsEnabled(false);
        objectMapper = new ObjectMapper();
        dbPath = tempDir.resolve("memory.duckdb");
        service = createService(dbPath);
    }

    private ConversationMemoryService createService(Path path) {
        DatabaseService database = new DatabaseService(path.toString(), objectMapper, memoryConfig);
        database.init();
        return new ConversationMemoryService(database, memoryConfig,
                new MemoryRedactor(memoryConfig), objectMapper, null);
    }

    @Test
    void conversationSurvivesServiceRecreation() {
        service.initConversation("thread-1", "System prompt");
        service.addUserMessage("thread-1", "Fix VULN-001");
        service.addAiMessage("thread-1", AiMessage.from("I will inspect it."));

        ConversationMemoryService recreated = createService(dbPath);
        List<ChatMessage> history = recreated.getHistory("thread-1");

        assertEquals(3, history.size());
        assertInstanceOf(SystemMessage.class, history.get(0));
        assertInstanceOf(UserMessage.class, history.get(1));
        assertEquals("I will inspect it.", recreated.getLastAiMessage("thread-1").text());
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
