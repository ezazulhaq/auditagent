package com.cb.auditagent.graph.node;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.agent.AgentStage;
import com.cb.auditagent.graph.agent.StageMilestone;
import com.cb.auditagent.graph.state.MultiAgentState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SupervisorContextCompressionTest {

    private SupervisorNode supervisorNode;

    @BeforeEach
    void setUp() {
        ChatModel mockChatModel = Mockito.mock(ChatModel.class);
        ConversationMemoryService mockMemoryService = Mockito.mock(ConversationMemoryService.class);
        AgentConfig agentConfig = new AgentConfig();
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
    void testMilestoneSummaryCappedAt150Tokens() {
        // Create an excessively verbose summary (300 words)
        StringBuilder verboseBuilder = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            verboseBuilder.append("word").append(i).append(" ");
        }

        StageMilestone milestone = new StageMilestone(
                AgentStage.DISCOVERY,
                true,
                verboseBuilder.toString(),
                List.of("src/main/java/Service.java")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("milestones", List.of(milestone));
        MultiAgentState state = new MultiAgentState(data);

        String context = supervisorNode.buildExecutionContext(state);
        assertNotNull(context);
        assertTrue(context.contains("... [truncated]"), "Summary exceeding 150 tokens must be truncated");

        // The summary in context should not exceed ~160 words
        String[] words = context.split("\\s+");
        assertTrue(words.length <= 175, "Milestone context must be strictly bounded per stage");
    }

    @Test
    void testLinearTokenGrowthVsQuadraticUncompressedContext() {
        List<AgentStage> stages = List.of(
                AgentStage.DISCOVERY,
                AgentStage.MEMORY_REVIEW,
                AgentStage.BASELINE,
                AgentStage.SYNTAX,
                AgentStage.FORMATTING,
                AgentStage.SYMBOLS,
                AgentStage.TYPING,
                AgentStage.CONTROL_FLOW,
                AgentStage.DATA_FLOW,
                AgentStage.ERROR_HANDLING,
                AgentStage.SECURITY,
                AgentStage.CONCURRENCY,
                AgentStage.PERFORMANCE,
                AgentStage.ARCHITECTURE,
                AgentStage.ROOT_CAUSE,
                AgentStage.POST_FIX
        );

        List<StageMilestone> milestones = new ArrayList<>();
        Map<String, String> rawOutputs = new LinkedHashMap<>();

        // Simulate 16 stages where uncompressed outputs accumulate prior stage logs (O(N^2))
        StringBuilder cumulativeBlob = new StringBuilder();
        for (AgentStage stage : stages) {
            cumulativeBlob.append("Detailed execution transcript for ").append(stage.name())
                    .append(" with full AST diffs, stack traces, compiler output, and raw tool JSON... ")
                    .append("REPEAT ".repeat(50)); // ~350 chars per stage
            rawOutputs.put(stage.name(), cumulativeBlob.toString());

            // Compact milestone (O(1) per stage, bounded at ~150 tokens)
            milestones.add(new StageMilestone(
                    stage,
                    true,
                    "Completed " + stage.getStageName() + " successfully without issues.",
                    List.of("src/main/Foo.java")
            ));
        }

        // Test with compact milestones (Phase 4)
        Map<String, Object> milestoneData = new HashMap<>();
        milestoneData.put("milestones", milestones);
        MultiAgentState milestoneState = new MultiAgentState(milestoneData);
        String milestoneContext = supervisorNode.buildExecutionContext(milestoneState);

        // Test with raw outputs (Legacy Phase 3 behavior)
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("agentOutputs", rawOutputs);
        MultiAgentState rawState = new MultiAgentState(rawData);
        String rawContext = supervisorNode.buildExecutionContext(rawState);

        // Verify milestone context scales strictly linearly O(N):
        // 16 stages * ~100 characters per milestone = ~1,600 - 3,000 characters
        int milestoneCharCount = milestoneContext.length();
        int rawCharCount = rawContext.length();

        assertTrue(milestoneCharCount < rawCharCount / 5,
                String.format("Milestone context (%d chars) must be dramatically smaller than uncompressed raw context (%d chars)",
                        milestoneCharCount, rawCharCount));

        // Average characters per stage in milestone context must remain strictly constant O(1)
        double avgCharsPerStage = (double) milestoneCharCount / stages.size();
        assertTrue(avgCharsPerStage < 250, "Average characters per milestone stage must be <= 250");
    }

    @Test
    void testBackwardCompatibilityWithAgentOutputsWhenMilestonesAbsent() {
        Map<String, String> outputs = Map.of(
                "DISCOVERY", "Found vulnerable SQL query in DatabaseService.java",
                "SECURITY", "Semgrep rule detected sql-injection"
        );
        Map<String, Object> data = new HashMap<>();
        data.put("agentOutputs", outputs);
        MultiAgentState state = new MultiAgentState(data);

        String context = supervisorNode.buildExecutionContext(state);
        assertTrue(context.contains("DISCOVERY output: Found vulnerable SQL query"));
        assertTrue(context.contains("SECURITY output: Semgrep rule detected"));
    }
}
