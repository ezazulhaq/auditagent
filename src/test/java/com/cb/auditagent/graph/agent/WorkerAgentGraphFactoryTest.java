package com.cb.auditagent.graph.agent;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.graph.node.EmptyResponseNode;
import com.cb.auditagent.graph.node.ModelCallNode;
import com.cb.auditagent.graph.node.NudgeNode;
import com.cb.auditagent.graph.node.StuckDetectionNode;
import com.cb.auditagent.graph.node.TimeoutNode;
import com.cb.auditagent.graph.node.ToolExecutionNode;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import com.cb.auditagent.service.RepositoryMemoryService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerAgentGraphFactoryTest {

    private ChatModel mockChatModel;
    private ConversationMemoryService mockMemoryService;
    private LlmService mockLlmService;
    private WorkerAgentGraphFactory factory;
    private Vulnerability vuln;

    @BeforeEach
    void setUp() {
        mockChatModel = Mockito.mock(ChatModel.class);
        mockMemoryService = Mockito.mock(ConversationMemoryService.class);
        mockLlmService = Mockito.mock(LlmService.class);
        RepositoryMemoryService mockRepoMemoryService = Mockito.mock(RepositoryMemoryService.class);

        AgentConfig config = new AgentConfig();
        config.setMaxIterations(6);
        config.setLlmCallTimeoutSeconds(30);
        config.setMaxConsecutiveEmptyResponses(3);

        when(mockLlmService.buildToolSpecifications()).thenReturn(List.of());
        when(mockMemoryService.getHistory(anyString(), anyString())).thenReturn(List.of());

        ModelCallNode modelCallNode = new ModelCallNode(mockChatModel, config, mockMemoryService, mockLlmService, null);
        ToolExecutionNode toolExecutionNode = new ToolExecutionNode(config, mockMemoryService, mockLlmService, mockRepoMemoryService);

        factory = new WorkerAgentGraphFactory(
                config,
                new MemorySaver(),
                modelCallNode,
                toolExecutionNode,
                new StuckDetectionNode(),
                new NudgeNode(mockMemoryService),
                new EmptyResponseNode(mockMemoryService),
                new TimeoutNode(),
                mockMemoryService
        );

        vuln = new Vulnerability("VULN-1", "src/app.ts", 12, "unsafe()", Severity.HIGH, "XSS", "desc", "typescript");
    }

    @Test
    void rootCauseWorkerNudgesWhenModelReturnsProseWithoutApplyingPatch() throws Exception {
        List<ChatMessage> history = new ArrayList<>();
        history.add(UserMessage.from("Fix XSS in src/app.ts"));
        when(mockMemoryService.getHistory("t-1", "r-1")).thenAnswer(inv -> new ArrayList<>(history));
        doAnswer(inv -> {
            history.add(inv.getArgument(2));
            return null;
        }).when(mockMemoryService).addAiMessage(eq("t-1"), eq("r-1"), any(AiMessage.class));
        doAnswer(inv -> {
            history.add(UserMessage.from((String) inv.getArgument(2)));
            return null;
        }).when(mockMemoryService).addUserMessage(eq("t-1"), eq("r-1"), anyString());
        doAnswer(inv -> {
            List<ToolExecutionRequest> reqs = inv.getArgument(2);
            List<String> res = inv.getArgument(3);
            for (int i = 0; i < reqs.size(); i++) {
                history.add(ToolExecutionResultMessage.from(reqs.get(i), res.get(i)));
            }
            return null;
        }).when(mockMemoryService).addToolResults(eq("t-1"), eq("r-1"), anyList(), anyList());

        AtomicInteger calls = new AtomicInteger(0);
        when(mockChatModel.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("You should sanitize the input in src/app.ts."))
                        .build();
            }
            if (n == 2) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("apply_patch")
                                .arguments("{\"filePath\":\"src/app.ts\",\"originalCode\":\"unsafe()\",\"replacementCode\":\"safe()\"}")
                                .build())))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Applied fix in src/app.ts."))
                    .build();
        });

        when(mockLlmService.executeToolCall(eq("apply_patch"), anyString(), anyString(), any()))
                .thenReturn("SUCCESS: Patch applied to src/app.ts.\nBACKUP_PATH: b\nBEFORE_HASH: 1\nAFTER_HASH: 2");
        when(mockLlmService.isSuccessfulToolResult(eq("apply_patch"), anyString())).thenReturn(true);

        CompiledGraph<RemediationState> graph = factory.createWorkerGraph(AgentStage.ROOT_CAUSE);
        Map<String, Object> init = new HashMap<>();
        init.put("threadId", "t-1");
        init.put("runId", "r-1");
        init.put("repoPath", "/repo");
        init.put("vulnerability", vuln);
        init.put("iteration", 0);
        init.put("maxIterations", 6);
        init.put("patchApplied", false);

        RemediationState result = graph.invoke(GraphInput.args(init), RunnableConfig.builder().threadId("sub-1").build())
                .orElseThrow();

        assertTrue(result.isPatchApplied());
        assertEquals(1, result.getRootCauseNudgeCount());
        verify(mockMemoryService, times(1)).addUserMessage(eq("t-1"), eq("r-1"), contains("NO code patch has been applied yet"));
    }

    @Test
    void extractTouchedFilesIgnoresReadOnlyTools() {
        Map<String, Object> data = new HashMap<>();
        data.put("vulnerability", vuln);
        data.put("patchApplied", false);
        data.put("trajectory", List.of(
                Map.of("action", "read_file", "args", "{\"filePath\":\"src/app.ts\"}", "result", "File: src/app.ts"),
                Map.of("action", "rescan_file", "args", "{\"filePath\":\"src/app.ts\"}", "result", "Rescan complete: 1 vulnerabilities")
        ));

        StageMilestone milestone = factory.createMilestone(AgentStage.DISCOVERY, new RemediationState(data));
        assertTrue(milestone.touchedFiles().isEmpty(), "Read-only stages must not mark files as modified");
    }

    @Test
    void extractTouchedFilesRecordsSuccessfulApplyPatch() {
        Map<String, Object> data = new HashMap<>();
        data.put("vulnerability", vuln);
        data.put("patchApplied", true);
        data.put("trajectory", List.of(
                Map.of("action", "read_file", "args", "{\"filePath\":\"src/other.ts\"}", "result", "File: src/other.ts"),
                Map.of("action", "apply_patch", "args", "{\"filePath\":\"src/app.ts\"}", "result", "SUCCESS: Patch applied")
        ));

        StageMilestone milestone = factory.createMilestone(AgentStage.ROOT_CAUSE, new RemediationState(data));
        assertEquals(List.of("src/app.ts"), milestone.touchedFiles());
    }
}
