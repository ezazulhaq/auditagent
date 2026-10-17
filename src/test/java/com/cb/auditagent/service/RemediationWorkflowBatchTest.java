package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.graph.DuckDbCheckpointSaver;
import com.cb.auditagent.graph.MultiAgentRemediationGraph;
import com.cb.auditagent.graph.RemediationGraph;
import com.cb.auditagent.graph.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemediationWorkflowBatchTest {

    private DatabaseService database;
    private SourceControlProvider sourceControl;
    private PullRequestProvider pullRequests;
    private GitHubApiClient github;
    private GitHubAuthService auth;
    private LlmService llm;
    private ObjectMapper objectMapper;
    private MemoryRedactor redactor;
    private RemediationWorkflowGraph workflowGraph;
    private RepositoryAccessService repositoryAccess;
    private ReporterService reporter;
    private AgentConfig agentConfig;

    private ValidateAccessNode validateAccessNode;
    private CloneWorkspaceNode cloneWorkspaceNode;
    private ApprovalReadyNode approvalReadyNode;
    private RejectNode rejectNode;
    private PublicationNode publicationNode;

    private RemediationWorkflowService service;
    private AuthenticatedUser user;
    private ManagedRepository repository;
    private ScanSnapshot snapshot;

    @BeforeEach
    void setUp() throws Exception {
        database = mock(DatabaseService.class);
        sourceControl = mock(SourceControlProvider.class);
        pullRequests = mock(PullRequestProvider.class);
        github = mock(GitHubApiClient.class);
        auth = mock(GitHubAuthService.class);
        llm = mock(LlmService.class);
        objectMapper = new ObjectMapper();
        redactor = new MemoryRedactor(new MemoryConfig());
        repositoryAccess = mock(RepositoryAccessService.class);
        reporter = mock(ReporterService.class);
        agentConfig = new AgentConfig();
        agentConfig.setBatchConcurrency(2);
        agentConfig.setUseMultiAgent(false);

        validateAccessNode = mock(ValidateAccessNode.class);
        cloneWorkspaceNode = mock(CloneWorkspaceNode.class);
        approvalReadyNode = mock(ApprovalReadyNode.class);
        rejectNode = mock(RejectNode.class);
        publicationNode = mock(PublicationNode.class);

        RemediationGraph remediationGraph = mock(RemediationGraph.class);
        @SuppressWarnings("unchecked")
        org.bsc.langgraph4j.CompiledGraph<com.cb.auditagent.graph.RemediationState> subGraph = mock(org.bsc.langgraph4j.CompiledGraph.class);
        when(remediationGraph.getGraph()).thenReturn(subGraph);
        when(subGraph.invoke(any(org.bsc.langgraph4j.GraphInput.class), any())).thenReturn(Optional.empty());

        workflowGraph = new RemediationWorkflowGraph(
                validateAccessNode, cloneWorkspaceNode, approvalReadyNode, rejectNode, publicationNode,
                remediationGraph, mock(MultiAgentRemediationGraph.class),
                mock(DuckDbCheckpointSaver.class), agentConfig);

        user = new AuthenticatedUser("github:42", 42, "testuser", "Test User", "test@example.com", "csrf-token");
        repository = new ManagedRepository(100L, 10L, "owner", "repo", "owner/repo",
                "https://github.com/owner/repo.git", "main", true, "WRITE");
        snapshot = new ScanSnapshot("report-key-1", 100L, "main", "base-sha-123",
                "rep-key", "{}", user.userId(), LocalDateTime.now());

        when(database.memoryThreadBelongsTo("thread-1", user.userId())).thenReturn(true);
        when(repositoryAccess.requireRepositoryAccess(eq(user), eq(100L))).thenReturn(repository);
        when(database.findLatestScanSnapshot(100L, "main")).thenReturn(Optional.of(snapshot));

        service = new RemediationWorkflowService(
                database, sourceControl, pullRequests, github, auth, llm, objectMapper,
                redactor, workflowGraph, repositoryAccess, reporter, agentConfig);
    }

    @Test
    void rejectsEmptyOrBlankVulnerabilityList() {
        assertThrows(IllegalArgumentException.class, () ->
                service.analyzeBatch(user, "thread-1", 100L, "main", List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                service.analyzeBatch(user, "thread-1", 100L, "main", Arrays.asList(" ", null)));
    }

    @Test
    void executesBatchRemediationWithIsolatedFailure() throws Exception {
        when(validateAccessNode.apply(any())).thenAnswer(inv -> {
            WorkflowState s = inv.getArgument(0);
            return s.data();
        });

        // For VULN-1: cloneWorkspaceNode succeeds, approvalReadyNode succeeds
        when(cloneWorkspaceNode.apply(argThat(s -> s != null && "VULN-1".equals(s.getVulnerabilityId()))))
                .thenAnswer(inv -> {
                    WorkflowState s = inv.getArgument(0);
                    Map<String, Object> data = new HashMap<>(s.data());
                    data.put("runId", "run-1");
                    return data;
                });

        ApprovalPreview preview1 = new ApprovalPreview("run-1", "VULN-1", "owner/repo", "main",
                "base-sha", "branch-fix-1", List.of("File.java"), "diff --git ...",
                Map.of("BUILD", "PASS"), "summary", "digest-1");

        when(approvalReadyNode.apply(argThat(s -> s != null && "VULN-1".equals(s.getVulnerabilityId()))))
                .thenAnswer(inv -> {
                    WorkflowState s = inv.getArgument(0);
                    Map<String, Object> data = new HashMap<>(s.data());
                    data.put("approvalPreview", preview1);
                    data.put("workflowMessage", "Remediation verified.");
                    return data;
                });

        // For VULN-2: cloneWorkspaceNode throws an exception
        when(cloneWorkspaceNode.apply(argThat(s -> s != null && "VULN-2".equals(s.getVulnerabilityId()))))
                .thenThrow(new IllegalStateException("Compilation failed in worker"));

        List<String> events = service.analyzeBatch(user, "thread-1", 100L, "main", List.of("VULN-1", "VULN-2"))
                .collectList()
                .block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        // Check for batch_start
        assertTrue(events.stream().anyMatch(e -> e.contains("\"type\":\"batch_start\"") && e.contains("\"total\":2")));

        // Check for VULN-1 steps and approval
        assertTrue(events.stream().anyMatch(e -> e.contains("VULN-1") && e.contains("\"type\":\"graph_step\"")));
        assertTrue(events.stream().anyMatch(e -> e.contains("VULN-1") && e.contains("\"type\":\"approval_ready\"")));

        // Check for VULN-2 failure event
        assertTrue(events.stream().anyMatch(e -> e.contains("VULN-2") && e.contains("Compilation failed in worker")));

        // Check for batch_complete with 1 succeeded and 1 failed
        assertTrue(events.stream().anyMatch(e -> e.contains("\"type\":\"batch_complete\"")
                && e.contains("\"completed\":1")
                && e.contains("\"failed\":1")));
    }
}
