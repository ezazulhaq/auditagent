package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Skill;
import com.cb.auditagent.domain.Vulnerability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * dev.langchain4j.model.chat.ChatModel to simulate multi-turn LLM interactions
 * without
 *
 * Updated for loop engineering gaps #1-#8.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LlmServiceAgentLoopTest {
    @MockitoBean
    private ChatModel springChatModel;
    @MockitoBean
    private SkillManagerService skillManager;
    @MockitoBean
    private dev.langchain4j.model.chat.ChatModel agentModel;
    @MockitoBean
    private AgentToolService agentToolService;

    @Autowired
    private ConversationMemoryService memoryService;
    @Autowired
    private AgentConfig agentConfig;
    @Autowired
    private LlmService llmService;

    private Vulnerability testVuln;

    @BeforeEach
    void setUp() {
        agentConfig.setMaxIterations(5);
        agentConfig.setMaxConsecutiveEmptyResponses(3);
        agentConfig.setLlmCallTimeoutSeconds(30);
        agentConfig.setMaxToolResultChars(4000);
        agentConfig.setMaxHistoryMessages(40);

        Skill mockSkill = mock(Skill.class);
        lenient().when(mockSkill.getInstructions()).thenReturn("You are an expert software engineer.");
        lenient().when(skillManager.getSkill("patch-engineer")).thenReturn(mockSkill);

        testVuln = new Vulnerability(
                "VULN-000001", "src/main/java/com/example/UserDao.java", 42,
                "String query = \"SELECT * FROM users WHERE id = \" + userId;",
                Severity.HIGH, "sql-injection",
                "Concatenating user input into SQL query creates SQL injection vulnerability.",
                "Java");
    }

    // =========================================================================
    // Scenario 1: Single-turn — LLM returns final text immediately (no tools)
    // =========================================================================

    @Test
    @DisplayName("Agent completes in 1 iteration when LLM returns text without tool calls")
    void agentLoop_noToolCalls_completesInOneIteration() {
        AiMessage directResponse = AiMessage.from("The vulnerability is a false positive. No fix needed.");
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(directResponse)
                .build();
        when(agentModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        List<Map<String, Object>> events = new ArrayList<>();
        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-1", testVuln, "/fake/repo", events::add);

        assertNotNull(result);
        assertEquals("The vulnerability is a false positive. No fix needed.", result.getFinalResponse());
        assertEquals(1, result.getIterationsUsed());
        assertFalse(result.isMaxIterationsReached());
        assertFalse(result.isPatchApplied()); // Gap #7
        assertNotNull(result.getTrajectory()); // Gap #8

        verify(agentModel, times(1)).chat(any(ChatRequest.class));
        assertTrue(events.stream().anyMatch(e -> "agent_thinking".equals(e.get("step"))));
        assertTrue(events.stream().anyMatch(e -> "agent_complete".equals(e.get("step"))));
    }

    // =========================================================================
    // Scenario 2: Multi-turn — LLM calls a tool, then returns text
    // =========================================================================

    @Test
    @DisplayName("Agent reads file via tool call, then completes with a fix summary")
    void agentLoop_singleToolCall_thenComplete() {
        ToolExecutionRequest readFileRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("read_file")
                .arguments("{\"filePath\":\"src/main/java/com/example/UserDao.java\",\"startLine\":0,\"endLine\":0}")
                .build();

        AiMessage toolCallMessage = AiMessage.from(List.of(readFileRequest));
        ChatResponse firstResponse = ChatResponse.builder()
                .aiMessage(toolCallMessage)
                .build();

        AiMessage finalMessage = AiMessage
                .from("I have analyzed the file. The fix involves using PreparedStatement.");
        ChatResponse secondResponse = ChatResponse.builder()
                .aiMessage(finalMessage)
                .build();

        when(agentModel.chat(any(ChatRequest.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        when(agentToolService.readFile(eq("/fake/repo"), eq("src/main/java/com/example/UserDao.java"), eq(0),
                eq(0)))
                .thenReturn(
                        "package com.example;\npublic class UserDao {\n  String query = \"SELECT * FROM users WHERE id = \" + userId;\n}");

        List<Map<String, Object>> events = new ArrayList<>();
        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-2", testVuln, "/fake/repo", events::add);

        assertEquals(2, result.getIterationsUsed());
        assertFalse(result.isMaxIterationsReached());
        assertFalse(result.isPatchApplied()); // Gap #7: only read, no patch
        assertTrue(result.getFinalResponse().contains("PreparedStatement"));

        verify(agentToolService).readFile("/fake/repo", "src/main/java/com/example/UserDao.java", 0, 0);
        verify(agentModel, times(2)).chat(any(ChatRequest.class));

        assertTrue(events.stream().anyMatch(e -> "tool_call".equals(e.get("step"))));
        assertTrue(events.stream().anyMatch(e -> "tool_result".equals(e.get("step"))));
    }

    // =========================================================================
    // Scenario 3: Full remediation — read → patch → compile → rescan → done
    // =========================================================================

    @Test
    @DisplayName("Full remediation cycle: read_file → apply_patch → compile → rescan → complete")
    void agentLoop_fullRemediationCycle() {
        agentConfig.setMaxIterations(6);
        AtomicInteger callCount = new AtomicInteger(0);

        when(agentModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            return switch (call) {
                case 1 -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t1").name("read_file")
                                .arguments(
                                        "{\"filePath\":\"src/main/java/com/example/UserDao.java\",\"startLine\":0,\"endLine\":0}")
                                .build())))
                        .build();
                case 2 -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t2").name("apply_patch")
                                .arguments(
                                        "{\"filePath\":\"src/main/java/com/example/UserDao.java\",\"originalCode\":\"\\\"SELECT * FROM users WHERE id = \\\" + userId\",\"replacementCode\":\"PreparedStatement ps = conn.prepareStatement(\\\"SELECT * FROM users WHERE id = ?\\\");\"}")
                                .build())))
                        .build();
                case 3 -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t3").name("compile_project")
                                .arguments("{}")
                                .build())))
                        .build();
                case 4 -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t4").name("rescan_file")
                                .arguments("{\"filePath\":\"src/main/java/com/example/UserDao.java\"}")
                                .build())))
                        .build();
                case 5 -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t5").name("run_tests")
                                .arguments("{}")
                                .build())))
                        .build();
                case 6 -> ChatResponse.builder().aiMessage(
                        AiMessage.from("Fix applied successfully. SQL injection resolved by using PreparedStatement."))
                        .build();
                default -> throw new IllegalStateException("Unexpected call " + call);
            };
        });

        when(agentToolService.readFile(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("String query = \"SELECT * FROM users WHERE id = \" + userId;");
        when(agentToolService.applyPatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("SUCCESS: Patch applied to UserDao.java");
        when(agentToolService.compileProject(anyString()))
                .thenReturn("BUILD SUCCESS");
        when(agentToolService.rescanFile(anyString(), anyString()))
                .thenReturn("Rescan complete: 0 vulnerabilities found in UserDao.java");
        when(agentToolService.runTests(anyString(), nullable(String.class)))
                .thenReturn("TEST SUCCESS: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0");

        List<Map<String, Object>> events = new ArrayList<>();
        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-3", testVuln, "/fake/repo", events::add);

        assertEquals(6, result.getIterationsUsed());
        assertFalse(result.isMaxIterationsReached());
        assertTrue(result.isPatchApplied()); // Gap #7: patch WAS applied
        assertTrue(result.getFinalResponse().contains("PreparedStatement"));

        verify(agentToolService).readFile(anyString(), anyString(), anyInt(), anyInt());
        verify(agentToolService).applyPatch(anyString(), anyString(), anyString(), anyString());
        verify(agentToolService).compileProject(anyString());
        verify(agentToolService).rescanFile(anyString(), anyString());
        verify(agentToolService).runTests(anyString(), nullable(String.class));

        // Gap #8: trajectory should exist
        assertEquals(6, result.getTrajectory().size());
    }

    // =========================================================================
    // Scenario 4: Max iterations hit
    // =========================================================================

    @Test
    @DisplayName("Agent reaches max iterations and reports maxIterationsReached=true")
    void agentLoop_maxIterationsReached() {
        agentConfig.setMaxIterations(3);

        when(agentModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("loop").name("read_file")
                                .arguments("{\"filePath\":\"pom.xml\",\"startLine\":0,\"endLine\":0}")
                                .build())))
                        .build());

        when(agentToolService.readFile(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("some file content");

        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-4", testVuln, "/fake/repo", e -> {
                });

        assertTrue(result.isMaxIterationsReached());
        assertEquals(3, result.getIterationsUsed());
        assertTrue(result.getFinalResponse().contains("maximum iterations"));
        assertFalse(result.isPatchApplied());
    }

    // =========================================================================
    // Scenario 5: Gap #5 — Consecutive empty responses capped at 3
    // =========================================================================

    @Test
    @DisplayName("Agent aborts after 3 consecutive empty responses (Gap #5)")
    void agentLoop_consecutiveEmptyResponses_aborts() {
        agentConfig.setMaxConsecutiveEmptyResponses(3);

        // Return null response directly (ChatResponse doesn't allow null aiMessage)
        when(agentModel.chat(any(ChatRequest.class))).thenReturn(null);

        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-5", testVuln, "/fake/repo", e -> {
                });

        // Should abort after 3 empty responses, not burn all 5 iterations
        assertTrue(result.getFinalResponse().contains("consecutive empty responses"));
        assertEquals(3, result.getIterationsUsed());
        assertFalse(result.isPatchApplied());
    }

    // =========================================================================
    // Scenario 6: Tool execution error — agent self-corrects
    // =========================================================================

    @Test
    @DisplayName("Tool execution exception is caught and injected as context")
    void agentLoop_toolExecutionError_selfCorrects() {
        AtomicInteger callCount = new AtomicInteger(0);

        when(agentModel.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t1").name("read_file")
                                .arguments("{\"filePath\":\"nonexistent.java\",\"startLine\":0,\"endLine\":0}")
                                .build())))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("File not found. Let me try a different approach."))
                    .build();
        });

        when(agentToolService.readFile(anyString(), eq("nonexistent.java"), anyInt(), anyInt()))
                .thenReturn("ERROR: File not found: nonexistent.java");

        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-6", testVuln, "/fake/repo", e -> {
                });

        assertEquals(2, result.getIterationsUsed());
        assertFalse(result.isMaxIterationsReached());
    }

    // =========================================================================
    // Scenario 7: Unknown tool name handled gracefully
    // =========================================================================

    @Test
    @DisplayName("Unknown tool name returns error string instead of crashing")
    void agentLoop_unknownTool_handledGracefully() {
        AtomicInteger callCount = new AtomicInteger(0);

        when(agentModel.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                        ToolExecutionRequest.builder()
                                .id("t1").name("nonexistent_tool")
                                .arguments("{}")
                                .build())))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("I tried an unknown tool. Let me fix this properly."))
                    .build();
        });

        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-7", testVuln, "/fake/repo", e -> {
                });

        assertEquals(2, result.getIterationsUsed());
        assertFalse(result.isMaxIterationsReached());
    }

    // =========================================================================
    // Scenario 8: SSE progress events have correct structure
    // =========================================================================

    @Test
    @DisplayName("Progress events contain required fields: type, step, message, iteration, maxIterations")
    void agentLoop_progressEvents_haveCorrectStructure() {
        when(agentModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("Done."))
                        .build());

        List<Map<String, Object>> events = new ArrayList<>();
        llmService.runAgentLoop("test-thread-8", testVuln, "/fake/repo", events::add);

        for (Map<String, Object> event : events) {
            assertNotNull(event.get("type"), "Event missing 'type'");
            assertNotNull(event.get("step"), "Event missing 'step'");
            assertNotNull(event.get("message"), "Event missing 'message'");
            assertNotNull(event.get("iteration"), "Event missing 'iteration'");
            assertNotNull(event.get("maxIterations"), "Event missing 'maxIterations'");

            assertEquals("agent_progress", event.get("type"), "Event type must be 'agent_progress'");
            assertTrue((int) event.get("iteration") >= 1, "Iteration must be >= 1");
            assertEquals(5, event.get("maxIterations"), "maxIterations should match config");
        }
    }

    // =========================================================================
    // Scenario 9: Conversation memory is populated correctly
    // =========================================================================

    @Test
    @DisplayName("Conversation memory contains system + user + AI messages after agent loop")
    void agentLoop_populatesConversationMemory() {
        when(agentModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("Analysis complete."))
                        .build());

        llmService.runAgentLoop("test-thread-9", testVuln, "/fake/repo", e -> {
        });

        assertTrue(memoryService.getHistorySize("test-thread-9") >= 3);

        AiMessage lastAi = memoryService.getLastAiMessage("test-thread-9");
        assertNotNull(lastAi);
        assertEquals("Analysis complete.", lastAi.text());
    }

    // =========================================================================
    // Scenario 10: Null skill gracefully falls back to default prompt
    // =========================================================================

    @Test
    @DisplayName("When patch-engineer skill is null, default system prompt is used")
    void agentLoop_nullSkill_usesDefaultPrompt() {
        when(skillManager.getSkill("patch-engineer")).thenReturn(null);

        when(agentModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("Done with default prompt."))
                        .build());

        LlmService.AgentResult result = llmService.runAgentLoop(
                "test-thread-10", testVuln, "/fake/repo", e -> {
                });

        assertNotNull(result);
        assertEquals("Done with default prompt.", result.getFinalResponse());
    }
}
