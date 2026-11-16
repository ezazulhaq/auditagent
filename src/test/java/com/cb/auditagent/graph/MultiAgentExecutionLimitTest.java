package com.cb.auditagent.graph;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.agent.StageMilestone;
import com.cb.auditagent.graph.node.SupervisorNode;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MultiAgentExecutionLimitTest {

    private SupervisorNode supervisorNode;
    private ChatModel mockChatModel;
    private AgentConfig agentConfig;

    @BeforeEach
    void setUp() {
        mockChatModel = Mockito.mock(ChatModel.class);
        ConversationMemoryService mockMemoryService = Mockito.mock(ConversationMemoryService.class);
        agentConfig = new AgentConfig();

        // Previous bug: maxIterations was 5, terminating multi-agent graph on hop 5.
        // Phase 4 fix: decoupled maxSupervisorHops = 25, while maxStageRetries = 3.
        agentConfig.setMaxIterations(5);
        agentConfig.setMaxSupervisorHops(25);
        agentConfig.setMaxStageRetries(3);
        agentConfig.setStructuredSupervisor(true);

        LlmService mockLlmService = Mockito.mock(LlmService.class);

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

    @Test
    void testExecutes10PlusConsecutiveStagesWithoutPrematureAbort() throws Exception {
        // Sequence of 12 distinct review stages
        List<AgentStage> stageSequence = List.of(
                AgentStage.DISCOVERY,
                AgentStage.MEMORY_REVIEW,
                AgentStage.BASELINE,
                AgentStage.SYNTAX,
                AgentStage.SYMBOLS,
                AgentStage.TYPING,
                AgentStage.CONTROL_FLOW,
                AgentStage.DATA_FLOW,
                AgentStage.ERROR_HANDLING,
                AgentStage.SECURITY,
                AgentStage.ROOT_CAUSE,
                AgentStage.POST_FIX
        );

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", "run-multi-12");
        stateData.put("threadId", "thread-multi-12");
        stateData.put("planGenerated", true);
        stateData.put("remediationPlan", "1. DISCOVERY ... 12. POST_FIX");
        stateData.put("hops", 0);
        stateData.put("retries", 0);

        List<StageMilestone> milestones = new ArrayList<>();

        for (int i = 0; i < stageSequence.size(); i++) {
            AgentStage nextStage = stageSequence.get(i);
            String jsonResponse = String.format("{\"nextAgent\": \"%s\", \"reasoning\": \"Step %d in plan\"}",
                    nextStage.name(), i + 1);

            when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                    .aiMessage(AiMessage.from(jsonResponse))
                    .build());

            MultiAgentState state = new MultiAgentState(stateData);
            Map<String, Object> updates = supervisorNode.apply(state);

            // Verify supervisor routes to the planned stage without aborting to FAIL
            assertNotEquals("FAIL", updates.get("nextAgent"),
                    "Supervisor must not abort on iteration/hop " + (i + 1));
            assertEquals(nextStage.name(), updates.get("nextAgent"));

            int currentHops = (Integer) updates.get("hops");
            assertEquals(i + 1, currentHops);

            // Update stateData for next simulated hop
            stateData.put("hops", currentHops);
            stateData.put("retries", currentHops);

            milestones.add(new StageMilestone(nextStage, true, "Stage " + nextStage.name() + " passed.", List.of("src/Service.java")));
            stateData.put("milestones", new ArrayList<>(milestones));
        }

        // Final step: routing to COMPLETE after 12 successful stages
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"nextAgent\": \"COMPLETE\", \"reasoning\": \"All 12 stages verified.\"}"))
                .build());

        MultiAgentState finalState = new MultiAgentState(stateData);
        Map<String, Object> finalUpdates = supervisorNode.apply(finalState);
        assertEquals("COMPLETE", finalUpdates.get("nextAgent"));
        assertEquals(13, finalUpdates.get("hops"));
    }

    @Test
    void testSupervisorAbortsWhenHopsExceedMaxSupervisorHops() throws Exception {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", "run-hop-exceeded");
        stateData.put("threadId", "thread-hop-exceeded");
        stateData.put("planGenerated", true);
        stateData.put("hops", 25); // At limit of 25 hops
        stateData.put("retries", 25);

        MultiAgentState state = new MultiAgentState(stateData);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"), "Must route to FAIL when maxSupervisorHops (25) is reached");
    }

    @Test
    void testSupervisorAbortsWhenStageRetriesExceedLimit() throws Exception {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", "run-retry-exceeded");
        stateData.put("threadId", "thread-retry-exceeded");
        stateData.put("planGenerated", true);
        stateData.put("hops", 6);
        stateData.put("stageRetries", Map.of("ROOT_CAUSE", 3)); // ROOT_CAUSE hit 3 failures

        MultiAgentState state = new MultiAgentState(stateData);
        Map<String, Object> updates = supervisorNode.apply(state);

        assertEquals("FAIL", updates.get("nextAgent"), "Must route to FAIL when any stage exceeds maxStageRetries (3)");
    }
}
