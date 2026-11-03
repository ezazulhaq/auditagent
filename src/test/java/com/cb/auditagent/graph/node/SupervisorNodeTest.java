package com.cb.auditagent.graph.node;

import com.cb.auditagent.client.jev.JevAnswer;
import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SupervisorNodeTest {

    private ChatModel mockChatModel;
    private ConversationMemoryService mockMemoryService;
    private AgentConfig agentConfig;
    private LlmService mockLlmService;
    private JevClient mockJevClient;
    private JevProperties jevProperties;
    private SupervisorNode supervisorNode;

    @BeforeEach
    void setUp() {
        mockChatModel = Mockito.mock(ChatModel.class);
        mockMemoryService = Mockito.mock(ConversationMemoryService.class);
        agentConfig = new AgentConfig();
        agentConfig.setMaxIterations(5);
        mockLlmService = Mockito.mock(LlmService.class);
        mockJevClient = Mockito.mock(JevClient.class);
        jevProperties = new JevProperties();
        jevProperties.setEnabled(true);
        jevProperties.getSupervisor().setEnabled(true);
        jevProperties.getSupervisor().setFallbackToChatModel(true);

        supervisorNode = new SupervisorNode(
                mockChatModel,
                mockMemoryService,
                agentConfig,
                mockLlmService,
                null,
                mockJevClient,
                jevProperties
        );
    }

    @Test
    void testMandatoryPlanningFirstStage() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("runId", "run-1");
        data.put("threadId", "thread-1");
        data.put("retries", 0);
        data.put("planGenerated", false);

        MultiAgentState state = new MultiAgentState(data);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("PLANNING", updates.get("nextAgent"));
        assertEquals(1, updates.get("retries"));
        verifyNoInteractions(mockJevClient);
        verifyNoInteractions(mockChatModel);
    }

    @Test
    void testRouteWithJevSuccess() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);

        JevResponse response = new JevResponse();
        response.setAnswers(Map.of("next_stage", new JevAnswer("DISCOVERY", 0.94)));
        when(mockJevClient.execute(any(), eq("supervisor_routing"), any(), any())).thenReturn(response);

        Map<String, Object> data = new HashMap<>();
        data.put("runId", "run-1");
        data.put("threadId", "thread-1");
        data.put("retries", 1);
        data.put("planGenerated", true);
        data.put("remediationPlan", "Step 1: DISCOVERY\nStep 2: SECURITY");
        data.put("agentOutputs", Map.of("PLANNING", "Plan created"));

        MultiAgentState state = new MultiAgentState(data);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("DISCOVERY", updates.get("nextAgent"));
        assertEquals(2, updates.get("retries"));
        verifyNoInteractions(mockChatModel); // Verify fallback LLM was NOT called
    }

    @Test
    void testRouteWithJevFallbackToChatModel() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);
        when(mockJevClient.execute(any(), eq("supervisor_routing"), any(), any()))
                .thenThrow(new RuntimeException("Jev 3s timeout"));

        ChatResponse mockChatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("SECURITY"))
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(mockChatResponse);

        Map<String, Object> data = new HashMap<>();
        data.put("runId", "run-1");
        data.put("threadId", "thread-1");
        data.put("retries", 1);
        data.put("planGenerated", true);
        data.put("remediationPlan", "Step 1: SECURITY");
        data.put("agentOutputs", Map.of());

        MultiAgentState state = new MultiAgentState(data);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("SECURITY", updates.get("nextAgent"));
        assertEquals(2, updates.get("retries"));
        verify(mockChatModel, times(1)).chat(any(ChatRequest.class)); // Verify fallback LLM was called!
    }

    @Test
    void testMaxIterationsExceededRoutesToFail() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("retries", 5);

        MultiAgentState state = new MultiAgentState(data);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"));
    }
}

