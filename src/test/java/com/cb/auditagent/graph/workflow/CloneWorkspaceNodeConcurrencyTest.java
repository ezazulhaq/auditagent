package com.cb.auditagent.graph.workflow;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.SourceControlProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloneWorkspaceNodeConcurrencyTest {

    @TempDir
    Path tempDir;

    private DatabaseService database;
    private SourceControlProvider sourceControl;
    private GitHubApiClient github;
    private AgentConfig agentConfig;
    private CloneWorkspaceNode node;

    private final AuthenticatedUser user = new AuthenticatedUser("user-1", 1L, "octo", "Octo Cat", "", "csrf");
    private final ManagedRepository repo = new ManagedRepository(100L, 200L, "org", "repo", "org/repo",
            "https://github.com/org/repo.git", "main", true, "WRITE");
    private final ScanSnapshot snapshot = new ScanSnapshot("snap-1", 100L, "main", "sha-1", "github:100:main",
            "/tmp/ws", "user-1", LocalDateTime.now(ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        sourceControl = mock(SourceControlProvider.class);
        github = mock(GitHubApiClient.class);
        agentConfig = new AgentConfig();

        when(database.getManagedRepository(100L)).thenReturn(Optional.of(repo));
        when(database.findLatestScanSnapshot(100L, "main")).thenReturn(Optional.of(snapshot));
        when(github.installationToken(200L)).thenReturn("inst-token");
        when(sourceControl.workspacePath(anyString())).thenAnswer(inv -> tempDir.resolve((String) inv.getArgument(0)));

        node = new CloneWorkspaceNode(database, sourceControl, github, agentConfig);
    }

    @Test
    void distinctFindingsExecuteConcurrentlyWithoutGlobalDatabaseLockPinning() throws Exception {
        Vulnerability vuln1 = new Vulnerability();
        vuln1.setId("VULN-001");
        vuln1.setRuleId("rule-1");
        vuln1.setFilePath("File1.java");
        vuln1.setCodeSnippet("snippet1");
        vuln1.setSeverity(Severity.HIGH);
        vuln1.setStatus(VulnerabilityStatus.DETECTED);
        vuln1.setFindingFingerprint("fp-001");

        Vulnerability vuln2 = new Vulnerability();
        vuln2.setId("VULN-002");
        vuln2.setRuleId("rule-2");
        vuln2.setFilePath("File2.java");
        vuln2.setCodeSnippet("snippet2");
        vuln2.setSeverity(Severity.MEDIUM);
        vuln2.setStatus(VulnerabilityStatus.DETECTED);
        vuln2.setFindingFingerprint("fp-002");

        when(database.getVulnerabilityById("VULN-001", "github:100:main")).thenReturn(Optional.of(vuln1));
        when(database.getVulnerabilityById("VULN-002", "github:100:main")).thenReturn(Optional.of(vuln2));
        when(database.getActiveManagedRunIds(eq(100L), eq("main"), anyString())).thenReturn(Collections.emptyList());

        AgentRunRecord run1 = new AgentRunRecord("run-100001", "thread-1", "VULN-001", "fp-001", "/tmp/ws",
                com.cb.auditagent.domain.AgentPhase.PREPARING_WORKSPACE,
                com.cb.auditagent.domain.AgentRunStatus.ACTIVE, 0, 0, 5, false, false, false, false,
                "{}", "", null, null, LocalDateTime.now(ZoneOffset.UTC));
        AgentRunRecord run2 = new AgentRunRecord("run-100002", "thread-2", "VULN-002", "fp-002", "/tmp/ws",
                com.cb.auditagent.domain.AgentPhase.PREPARING_WORKSPACE,
                com.cb.auditagent.domain.AgentRunStatus.ACTIVE, 0, 0, 5, false, false, false, false,
                "{}", "", null, null, LocalDateTime.now(ZoneOffset.UTC));

        CountDownLatch vuln1InsideLock = new CountDownLatch(1);
        CountDownLatch allowVuln1Finish = new CountDownLatch(1);
        CountDownLatch vuln2Finished = new CountDownLatch(1);

        when(database.createManagedAgentRun(eq("thread-1"), eq(vuln1), anyString(), anyInt())).thenAnswer(inv -> {
            vuln1InsideLock.countDown();
            allowVuln1Finish.await(2, TimeUnit.SECONDS);
            return run1;
        });

        when(database.createManagedAgentRun(eq("thread-2"), eq(vuln2), anyString(), anyInt())).thenReturn(run2);

        // Start remediation node for finding 1 on a virtual thread
        WorkflowState state1 = new WorkflowState(Map.of(
                "threadId", "thread-1",
                "repositoryId", 100L,
                "branch", "main",
                "vulnerabilityId", "VULN-001",
                "authenticatedUser", user
        ));

        Thread.startVirtualThread(() -> {
            try {
                node.apply(state1);
            } catch (Exception e) {
                fail("Finding 1 failed: " + e.getMessage());
            }
        });

        assertTrue(vuln1InsideLock.await(1, TimeUnit.SECONDS), "Finding 1 should enter preparation lock");

        // Concurrently execute finding 2 for a different fingerprint
        WorkflowState state2 = new WorkflowState(Map.of(
                "threadId", "thread-2",
                "repositoryId", 100L,
                "branch", "main",
                "vulnerabilityId", "VULN-002",
                "authenticatedUser", user
        ));

        AtomicBoolean vuln2Success = new AtomicBoolean(false);
        Thread.startVirtualThread(() -> {
            try {
                Map<String, Object> updates = node.apply(state2);
                if ("run-100002".equals(updates.get("runId"))) {
                    vuln2Success.set(true);
                }
            } catch (Exception e) {
                fail("Finding 2 failed: " + e.getMessage());
            } finally {
                vuln2Finished.countDown();
            }
        });

        // Finding 2 MUST NOT be blocked by Finding 1
        assertTrue(vuln2Finished.await(1, TimeUnit.SECONDS),
                "Finding 2 must complete without being blocked by Finding 1's active lock");
        assertTrue(vuln2Success.get());

        allowVuln1Finish.countDown();
    }
}
