package com.cb.auditagent.graph.node;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.agent.SupervisorDecision;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupervisorNodeStructuredTest {

    private ChatModel mockChatModel;
    private ConversationMemoryService mockMemoryService;
    private AgentConfig agentConfig;
    private LlmService mockLlmService;
    private SupervisorNode supervisorNode;

    @BeforeEach
    void setUp() {
        mockChatModel = Mockito.mock(ChatModel.class);
        mockMemoryService = Mockito.mock(ConversationMemoryService.class);
        agentConfig = new AgentConfig();
        agentConfig.setMaxSupervisorHops(25);
        agentConfig.setMaxStageRetries(3);
        agentConfig.setStructuredSupervisor(true);

        mockLlmService = Mockito.mock(LlmService.class);

        supervisorNode = new SupervisorNode(
                mockChatModel,
                mockMemoryService,
                agentConfig,
                mockLlmService,
                null,
                null,
                null
        );
    }

    private MultiAgentState createStateWithPlan() {
        Map<String, Object> data = new HashMap<>();
        data.put("runId", "run-struct-1");
        data.put("threadId", "thread-struct-1");
        data.put("hops", 1);
        data.put("retries", 1);
        data.put("planGenerated", true);
        data.put("remediationPlan", "1. DISCOVERY\n2. SECURITY\n3. ROOT_CAUSE");
        return new MultiAgentState(data);
    }

    @Test
    void testStructuredJsonResponseDeserializationAndValidation() throws Exception {
        String jsonReply = "{\"nextAgent\": \"DISCOVERY\", \"reasoning\": \"Inspecting repository files for root cause.\"}";
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(jsonReply))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("DISCOVERY", updates.get("nextAgent"));
        assertEquals("Inspecting repository files for root cause.", updates.get("supervisorReasoning"));
        assertEquals(2, updates.get("hops"));
        assertEquals(2, updates.get("retries"));

        // Verify ChatRequest requested JSON response format
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockChatModel).chat(captor.capture());
        assertEquals(ResponseFormat.JSON, captor.getValue().responseFormat());
    }

    @Test
    void testMarkdownWrappedJsonCodeBlock() throws Exception {
        String markdownJson = "```json\n{\"nextAgent\": \"SECURITY\", \"reasoning\": \"Running semgrep rescan to verify security posture.\"}\n```";
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(markdownJson))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("SECURITY", updates.get("nextAgent"));
        assertEquals("Running semgrep rescan to verify security posture.", updates.get("supervisorReasoning"));
    }

    @Test
    void testJsonFieldAliases() throws Exception {
        String aliasedJson = "{\"next_agent\": \"ROOT_CAUSE\", \"rationale\": \"Applying minimal verified fix.\"}";
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(aliasedJson))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("ROOT_CAUSE", updates.get("nextAgent"));
        assertEquals("Applying minimal verified fix.", updates.get("supervisorReasoning"));
    }

    @Test
    void testFuzzyEnumNormalizationAcrossVariations() {
        assertEquals("DISCOVERY", supervisorNode.normalizeAgentEnum("discovery"));
        assertEquals("DISCOVERY", supervisorNode.normalizeAgentEnum("**DISCOVERY**"));
        assertEquals("DISCOVERY", supervisorNode.normalizeAgentEnum("`discovery`"));
        assertEquals("ROOT_CAUSE", supervisorNode.normalizeAgentEnum("root-cause"));
        assertEquals("ROOT_CAUSE", supervisorNode.normalizeAgentEnum("root cause"));
        assertEquals("DATA_FLOW", supervisorNode.normalizeAgentEnum("data-flow"));
        assertEquals("POST_FIX", supervisorNode.normalizeAgentEnum("post fix"));
        assertEquals("COMPLETE", supervisorNode.normalizeAgentEnum("complete"));
        assertEquals("COMPLETE", supervisorNode.normalizeAgentEnum("COMPLETE."));
        assertEquals("FAIL", supervisorNode.normalizeAgentEnum("fail"));

        // Disallow re-routing to PLANNING
        assertNull(supervisorNode.normalizeAgentEnum("PLANNING"));
        assertNull(supervisorNode.normalizeAgentEnum("planning"));

        // Unknown agents default to null
        assertNull(supervisorNode.normalizeAgentEnum("UNKNOWN_AGENT_XYZ"));
    }

    @Test
    void testFallbackToFuzzyTextMatchingWhenModelFailsJson() throws Exception {
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("I recommend invoking **POST_FIX** stage next."))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("POST_FIX", updates.get("nextAgent"));
        assertNotNull(updates.get("supervisorReasoning"));
    }

    @Test
    void testUnknownAgentDefaultsToFail() throws Exception {
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"nextAgent\": \"GIBBERISH_STAGE\", \"reasoning\": \"Invalid\"}"))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"));
    }

    @Test
    void testReRoutingToPlanningDefaultsToFail() throws Exception {
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"nextAgent\": \"PLANNING\", \"reasoning\": \"Re-planning\"}"))
                .build());

        MultiAgentState state = createStateWithPlan();
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"));
    }

    @Test
    void testStageRetryLimitExceededRoutesToFail() throws Exception {
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"nextAgent\": \"ROOT_CAUSE\", \"reasoning\": \"Trying patch again\"}"))
                .build());

        Map<String, Object> data = new HashMap<>(createStateWithPlan().data());
        // ROOT_CAUSE has already failed 3 times (maxStageRetries = 3)
        data.put("stageRetries", Map.of("ROOT_CAUSE", 3));

        MultiAgentState state = new MultiAgentState(data);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"));
    }
}
