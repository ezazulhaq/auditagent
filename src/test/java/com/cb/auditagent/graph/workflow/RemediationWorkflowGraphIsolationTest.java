package com.cb.auditagent.graph.workflow;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.graph.DatabaseCheckpointSaver;
import com.cb.auditagent.graph.MultiAgentRemediationGraph;
import com.cb.auditagent.graph.RemediationGraph;
import com.cb.auditagent.graph.state.MultiAgentState;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemediationWorkflowGraphIsolationTest {

    @SuppressWarnings("unchecked")
    @Test
    void agentLoopScopesInnerGraphThreadIdByRunId() throws Exception {
        AgentConfig config = new AgentConfig();
        config.setUseMultiAgent(true);

        DatabaseCheckpointSaver saver = mock(DatabaseCheckpointSaver.class);
        when(saver.list(any())).thenReturn(List.of());
        when(saver.get(any())).thenReturn(java.util.Optional.empty());
        when(saver.put(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(saver.release(any())).thenCallRealMethod();

        CompiledGraph<MultiAgentState> mockInnerGraph = mock(CompiledGraph.class);
        when(mockInnerGraph.invoke(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(java.util.Optional.of(new MultiAgentState(Map.of("patchApplied", true))));

        MultiAgentRemediationGraph multiAgent = mock(MultiAgentRemediationGraph.class);
        when(multiAgent.getGraph()).thenReturn(mockInnerGraph);

        ValidateAccessNode validateAccessNode = mock(ValidateAccessNode.class);
        when(validateAccessNode.apply(any())).thenReturn(Map.of("workflowMessage", "ok"));

        CloneWorkspaceNode cloneWorkspaceNode = mock(CloneWorkspaceNode.class);
        Vulnerability vuln = new Vulnerability("VULN-15254E531F5F", "functions/src/email/inactivity-reminder.ts",
                67, "code", Severity.HIGH, "LOG", "desc", "typescript");
        when(cloneWorkspaceNode.apply(any())).thenReturn(Map.of(
                "runId", "run-abc",
                "vulnerability", vuln,
                "repoPath", "/tmp/ws"
        ));

        ApprovalReadyNode approvalReadyNode = mock(ApprovalReadyNode.class);
        when(approvalReadyNode.apply(any())).thenReturn(Map.of("workflowMessage", "ready"));

        RemediationWorkflowGraph workflowGraph = new RemediationWorkflowGraph(
                validateAccessNode,
                cloneWorkspaceNode,
                approvalReadyNode,
                mock(RejectNode.class),
                mock(PublicationNode.class),
                mock(RemediationGraph.class),
                multiAgent,
                saver,
                config
        );

        Map<String, Object> init = Map.of(
                "authenticatedUser", new AuthenticatedUser("u1", 1L, "octo", "Octo", "", "csrf"),
                "threadId", "82edbee0-83d9-4729-81c5-5a17ce578e1b",
                "repositoryId", 10L,
                "branch", "main",
                "vulnerabilityId", "VULN-15254E531F5F"
        );

        workflowGraph.getGraph().invoke(
                GraphInput.args(init),
                RunnableConfig.builder().threadId("82edbee0-83d9-4729-81c5-5a17ce578e1b-VULN-15254E531F5F").build()
        );

        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(mockInnerGraph).invoke(any(GraphInput.class), configCaptor.capture());

        String innerThreadId = configCaptor.getValue().threadId().orElse("");
        assertEquals("82edbee0-83d9-4729-81c5-5a17ce578e1b:run-abc", innerThreadId,
                "Inner multi-agent graph must scope checkpoint threadId by runId so prior runs on the same conversation thread cannot leak");
    }

    @Test
    void memorySaverWithSameConversationThreadIdIsolatedAcrossRunsWhenScopedByRunId() throws Exception {
        // Verify end-to-end with a real LangGraph4j StateGraph + MemorySaver that scoping by runId prevents stale state bleed
        MemorySaver saver = new MemorySaver();
        StateGraph<MultiAgentState> sg = new StateGraph<>(MultiAgentState::new);
        sg.addNode("init", org.bsc.langgraph4j.action.AsyncNodeAction.node_async(s -> Map.of("hops", s.getHops() + 1)));
        sg.addEdge(StateGraph.START, "init");
        sg.addEdge("init", StateGraph.END);
        CompiledGraph<MultiAgentState> compiled = sg.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        MultiAgentState run1 = compiled.invoke(
                GraphInput.args(Map.of("planGenerated", true, "finalResponse", "old-vuln-summary")),
                RunnableConfig.builder().threadId("conv-1:run-1").build()
        ).orElseThrow();
        assertEquals("old-vuln-summary", run1.data().get("finalResponse"));

        MultiAgentState run2 = compiled.invoke(
                GraphInput.args(Map.of("planGenerated", false)),
                RunnableConfig.builder().threadId("conv-1:run-2").build()
        ).orElseThrow();
        assertFalse(run2.isPlanGenerated());
        assertNull(run2.data().get("finalResponse"));
    }
}
