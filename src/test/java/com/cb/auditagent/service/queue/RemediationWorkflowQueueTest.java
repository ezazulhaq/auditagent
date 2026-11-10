package com.cb.auditagent.service.queue;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.dto.queue.RemediationTaskPayload;
import com.cb.auditagent.graph.workflow.RemediationWorkflowGraph;
import com.cb.auditagent.service.*;
import com.cb.auditagent.service.realtime.EventBroadcastService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemediationWorkflowQueueTest {

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
    private TaskProducerService taskProducerService;
    private EventBroadcastService eventBroadcastService;

    private RemediationWorkflowService remediationWorkflow;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        sourceControl = mock(SourceControlProvider.class);
        pullRequests = mock(PullRequestProvider.class);
        github = mock(GitHubApiClient.class);
        auth = mock(GitHubAuthService.class);
        llm = mock(LlmService.class);
        objectMapper = new ObjectMapper();
        redactor = mock(MemoryRedactor.class);
        workflowGraph = mock(RemediationWorkflowGraph.class);
        repositoryAccess = mock(RepositoryAccessService.class);
        reporter = mock(ReporterService.class);
        taskProducerService = mock(TaskProducerService.class);
        eventBroadcastService = mock(EventBroadcastService.class);

        remediationWorkflow = new RemediationWorkflowService(
                database, sourceControl, pullRequests, github, auth, llm,
                objectMapper, redactor, workflowGraph, repositoryAccess, reporter,
                new AgentConfig(), taskProducerService, eventBroadcastService
        );
    }

    @Test
    void shouldEnqueueTaskAndStreamWhenMessagingAvailable() {
        AuthenticatedUser user = new AuthenticatedUser("github:42", 42L, "octocat", "Octo", null, "csrf");
        ManagedRepository repository = new ManagedRepository(100L, 10L, "octocat", "demo", "octocat/demo",
                "https://github.com/octocat/demo.git", "main", true, "WRITE");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 100L, "main", "sha-abc",
                "report-1", "/tmp/ws", "github:42", LocalDateTime.now());

        when(database.memoryThreadBelongsTo("thread-1", "github:42")).thenReturn(true);
        when(repositoryAccess.requireRepositoryAccess(user, 100L)).thenReturn(repository);
        when(database.findLatestScanSnapshot(100L, "main")).thenReturn(Optional.of(snapshot));
        when(eventBroadcastService.listenEvents(anyString())).thenReturn(Flux.just("data: {\"type\":\"queued\"}\n\n"));

        Flux<String> stream = remediationWorkflow.analyze(user, "thread-1", 100L, "main", "VULN-000042");
        List<String> events = stream.collectList().block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("\"type\":\"queued\""));

        verify(eventBroadcastService).listenEvents(startsWith("remed-"));
        verify(eventBroadcastService).broadcastEvent(startsWith("remed-"), isNull(), eq("queued"), anyMap());

        ArgumentCaptor<RemediationTaskPayload> payloadCaptor = ArgumentCaptor.forClass(RemediationTaskPayload.class);
        verify(taskProducerService).enqueueRemediation(payloadCaptor.capture(), eq(10));

        RemediationTaskPayload payload = payloadCaptor.getValue();
        assertEquals("github:42", payload.userId());
        assertEquals("thread-1", payload.threadId());
        assertEquals(100L, payload.repositoryId());
        assertEquals("main", payload.branch());
        assertEquals("VULN-000042", payload.vulnerabilityId());
    }

    @Test
    void shouldEnqueueRemediationJobDirectly() {
        AuthenticatedUser user = new AuthenticatedUser("github:42", 42L, "octocat", "Octo", null, "csrf");
        ManagedRepository repository = new ManagedRepository(100L, 10L, "octocat", "demo", "octocat/demo",
                "https://github.com/octocat/demo.git", "main", true, "WRITE");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 100L, "main", "sha-abc",
                "report-1", "/tmp/ws", "github:42", LocalDateTime.now());

        when(database.memoryThreadBelongsTo("thread-1", "github:42")).thenReturn(true);
        when(repositoryAccess.requireRepositoryAccess(user, 100L)).thenReturn(repository);
        when(database.findLatestScanSnapshot(100L, "main")).thenReturn(Optional.of(snapshot));

        String jobId = remediationWorkflow.enqueueRemediationJob(user, "thread-1", 100L, "main", "VULN-000042", null, 5);

        assertNotNull(jobId);
        assertTrue(jobId.startsWith("remed-"));
        verify(eventBroadcastService).broadcastEvent(eq(jobId), isNull(), eq("queued"), anyMap());
        verify(taskProducerService).enqueueRemediation(any(RemediationTaskPayload.class), eq(5));
    }
}
