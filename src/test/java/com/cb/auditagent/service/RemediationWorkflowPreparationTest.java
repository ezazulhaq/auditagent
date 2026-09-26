package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.LlmService;
import com.cb.auditagent.service.MemoryRedactor;
import com.cb.auditagent.service.PullRequestProvider;
import com.cb.auditagent.service.RemediationWorkflowService;
import com.cb.auditagent.service.ReporterService;
import com.cb.auditagent.service.RepositoryAccessService;
import com.cb.auditagent.service.SourceControlProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemediationWorkflowPreparationTest {
        @TempDir
        Path tempDir;

        @Test
        void persistsRunAndPublicationBeforeCloneAndConflictsOnStaleBase() throws Exception {
                DatabaseService database = mock(DatabaseService.class);
                SourceControlProvider source = mock(SourceControlProvider.class);
                GitHubApiClient github = mock(GitHubApiClient.class);
                GitHubAuthService auth = mock(GitHubAuthService.class);
                AuthenticatedUser user = new AuthenticatedUser("github:99", 99, "octocat", "Octo", "", "csrf");
                ManagedRepository repository = new ManagedRepository(42, 9, "octo", "repo", "octo/repo",
                                "https://github.test/octo/repo.git", "main", true, "WRITE");
                ScanSnapshot snapshot = new ScanSnapshot("snapshot", 42, "main", "base-sha",
                                "github:42:main", "", user.userId(), LocalDateTime.now());
                Vulnerability vulnerability = new Vulnerability("VULN-000001", "src/UserDao.java", 10, "unsafe",
                                Severity.HIGH, "SQL Injection", "Unsafe query", "java");
                vulnerability.setFindingFingerprint("fingerprint");
                Path workspace = tempDir.resolve("managed-run");
                AgentRunRecord run = new AgentRunRecord("12345678-run", "thread-1", vulnerability.getId(),
                                "fingerprint", workspace.toString(), AgentPhase.PREPARING_WORKSPACE,
                                AgentRunStatus.ACTIVE,
                                0, 0, 15, false, false, false, false, null, null, null, null, LocalDateTime.now());

                when(database.memoryThreadBelongsTo("thread-1", user.userId())).thenReturn(true);
                when(database.getManagedRepository(42)).thenReturn(Optional.of(repository));
                when(auth.accessToken(user.userId())).thenReturn("user-token");
                when(source.userCanRead("user-token", repository)).thenReturn(true);
                when(database.findLatestScanSnapshot(42, "main")).thenReturn(Optional.of(snapshot));
                when(database.getVulnerabilityById(vulnerability.getId(), snapshot.reportKey()))
                                .thenReturn(Optional.of(vulnerability));
                when(database.hasActiveManagedRun(42, "main", "fingerprint")).thenReturn(false);
                when(source.workspacePath(anyString())).thenReturn(workspace);
                when(database.createManagedAgentRun("thread-1", vulnerability, workspace.toString(), 15))
                                .thenReturn(run);
                when(github.installationToken(9)).thenReturn("installation-token");
                when(source.cloneAtCommit(eq(repository), eq("main"), eq("base-sha"),
                                eq("installation-token"), anyString()))
                                .thenThrow(new IllegalStateException("STALE_BASE: branch advanced"));
                MemoryRedactor redactor = mock(MemoryRedactor.class);
                when(redactor.redactAndCap(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));

                com.cb.auditagent.graph.workflow.ValidateAccessNode validateAccessNode = new com.cb.auditagent.graph.workflow.ValidateAccessNode(
                                database, auth, source);
                com.cb.auditagent.graph.workflow.CloneWorkspaceNode cloneWorkspaceNode = new com.cb.auditagent.graph.workflow.CloneWorkspaceNode(
                                database, source, github, new AgentConfig());
                com.cb.auditagent.graph.workflow.ApprovalReadyNode approvalReadyNode = mock(
                                com.cb.auditagent.graph.workflow.ApprovalReadyNode.class);
                com.cb.auditagent.graph.workflow.RejectNode rejectNode = mock(
                                com.cb.auditagent.graph.workflow.RejectNode.class);
                com.cb.auditagent.graph.workflow.PublicationNode publicationNode = mock(
                                com.cb.auditagent.graph.workflow.PublicationNode.class);
                com.cb.auditagent.graph.workflow.RemediationWorkflowGraph workflowGraph = new com.cb.auditagent.graph.workflow.RemediationWorkflowGraph(
                                validateAccessNode, cloneWorkspaceNode, approvalReadyNode, rejectNode, publicationNode,
                                mock(com.cb.auditagent.graph.RemediationGraph.class),
                                mock(com.cb.auditagent.graph.MultiAgentRemediationGraph.class),
                                mock(com.cb.auditagent.graph.DuckDbCheckpointSaver.class),
                                new AgentConfig());

                RepositoryAccessService repoAccess = new RepositoryAccessService(database, source, auth);
                RemediationWorkflowService service = new RemediationWorkflowService(database, source,
                                mock(PullRequestProvider.class),
                                github, auth, mock(LlmService.class), new ObjectMapper(),
                                new MemoryRedactor(new MemoryConfig()), workflowGraph, repoAccess,
                                mock(ReporterService.class));

                List<String> events = service.analyze(user, "thread-1", 42, "main", vulnerability.getId())
                                .collectList().block();

                InOrder order = inOrder(database, source);
                order.verify(database).createManagedAgentRun("thread-1", vulnerability, workspace.toString(), 15);
                order.verify(database).createRunPublication(any(RunPublicationRecord.class));
                order.verify(source).cloneAtCommit(eq(repository), eq("main"), eq("base-sha"),
                                eq("installation-token"), anyString());
                verify(database).updateAgentRun(eq(run.runId()), eq(AgentPhase.CONFLICTED),
                                eq(AgentRunStatus.CONFLICTED), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                                anyBoolean(), anyBoolean(), any(), any(), eq("STALE_BASE"), anyString());
                verify(database).updateVulnerabilityStatus(vulnerability.getId(), VulnerabilityStatus.DETECTED, null);
                verify(source).cleanupWorkspace(workspace);
                assertTrue(events.stream().anyMatch(event -> event.contains("STALE_BASE")
                                || event.contains("advanced since the last scan")));
        }
}
