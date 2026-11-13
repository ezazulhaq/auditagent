package com.cb.auditagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanOrchestratorServiceTest {
        @TempDir
        Path workspace;

        private final AuthenticatedUser user = new AuthenticatedUser(
                        "github:99", 99, "octocat", "Octo Cat", "", "csrf");
        private DatabaseService database;
        private SourceControlProvider source;
        private ScannerService scanner;
        private ScanOrchestratorService service;

        @BeforeEach
        void setUp() {
                database = mock(DatabaseService.class);
                source = mock(SourceControlProvider.class);
                scanner = mock(ScannerService.class);
                GitHubApiClient github = mock(GitHubApiClient.class);
                GitHubAuthService auth = mock(GitHubAuthService.class);
                ManagedRepository repository = new ManagedRepository(42, 9, "octo", "repo", "octo/repo",
                                "https://github.test/octo/repo.git", "main", true, "WRITE");

                when(database.memoryThreadBelongsTo("thread-1", user.userId())).thenReturn(true);
                when(database.getManagedRepository(42)).thenReturn(Optional.of(repository));
                when(database.findLatestScanSnapshot(42, "main")).thenReturn(Optional.empty());
                when(auth.accessToken(user.userId())).thenReturn("user-token");
                when(source.userCanRead("user-token", repository)).thenReturn(true);
                when(github.installationToken(9)).thenReturn("installation-token");
                when(source.branchHead(repository, "main", "installation-token")).thenReturn("base-sha");
                when(source.cloneAtCommit(eq(repository), eq("main"), eq("base-sha"),
                                eq("installation-token"), anyString())).thenReturn(workspace);

                RepositoryAccessService repoAccess = new RepositoryAccessService(database, source, auth);
                service = new ScanOrchestratorService(database, source, github, scanner, mock(ReporterService.class),
                                repoAccess);
        }

        @Test
        void timeoutProducesTerminalErrorWithoutPersistingAFalseReportAndCleansWorkspace() {
                doThrow(new ScanTimeoutException("Semgrep exceeded the configured timeout of 900 seconds."))
                                .when(scanner).scan(eq(workspace.toString()), any());

                List<String> events = service.scan(user, "thread-1", 42, "main", "semgrep", true)
                                .collectList().block(Duration.ofSeconds(5));

                assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"error\"")
                                && event.contains("\"code\":\"SCAN_TIMEOUT\"")));
                verify(database, never()).saveReport(anyString(), anyString(), anyString(), any(), any());
                verify(database, never()).saveScanSnapshot(any());
                verify(source).cleanupWorkspace(workspace);
        }

        @Test
        void scannerFailureProducesScanFailedTerminalError() {
                doThrow(new ScanExecutionException("Semgrep returned an invalid JSON report."))
                                .when(scanner).scan(eq(workspace.toString()), any());

                List<String> events = service.scan(user, "thread-1", 42, "main", "semgrep", true)
                                .collectList().block(Duration.ofSeconds(5));

                assertTrue(events.stream().anyMatch(event -> event.contains("error") && event.contains("SCAN_FAILED")));
                verify(database, never()).saveReport(anyString(), anyString(), anyString(), any(), any());
                verify(database, never()).saveScanSnapshot(any());
                verify(source).cleanupWorkspace(workspace);
        }

        @Test
        void executeScanTaskBroadcastsCompleteEventWithFindingsAndHtmlReport() {
                ReporterService reporter = mock(ReporterService.class);
                when(reporter.generateHtml(any(), any(), any())).thenReturn("<html>findings report</html>");
                when(reporter.generateMarkdown(any(), any(), any())).thenReturn("# Report");

                com.cb.auditagent.service.realtime.EventBroadcastService eventBroadcastService =
                                mock(com.cb.auditagent.service.realtime.EventBroadcastService.class);

                GitHubAuthService auth = mock(GitHubAuthService.class);
                RepositoryAccessService repoAccess = new RepositoryAccessService(database, source, auth);
                GitHubApiClient github = mock(GitHubApiClient.class);
                when(github.installationToken(9)).thenReturn("installation-token");

                ScanOrchestratorService orchestrator = new ScanOrchestratorService(
                                database, source, github, scanner, reporter, repoAccess,
                                null, null, eventBroadcastService, null
                );

                com.cb.auditagent.domain.Vulnerability vuln = new com.cb.auditagent.domain.Vulnerability(
                                "VULN-001", "App.java", 10, "query = ...", com.cb.auditagent.domain.Severity.HIGH,
                                "SQL_INJECTION", "Vulnerable query", "java"
                );
                ScannerService.ScanResult result = new ScannerService.ScanResult(List.of(vuln), 3, 120, 0);
                when(scanner.scan(eq(workspace.toString()), any())).thenReturn(result);

                com.cb.auditagent.dto.queue.ScanTaskPayload task = new com.cb.auditagent.dto.queue.ScanTaskPayload(
                                "job-101", user.userId(), "thread-1", 42, "main", "base-sha",
                                "semgrep", true, java.time.Instant.now()
                );

                orchestrator.executeScanTask(task);

                org.mockito.ArgumentCaptor<java.util.Map<String, Object>> payloadCaptor =
                                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
                verify(eventBroadcastService).broadcastEvent(
                                eq("job-101"),
                                org.mockito.ArgumentMatchers.isNull(),
                                eq("complete"),
                                payloadCaptor.capture()
                );

                java.util.Map<String, Object> payload = payloadCaptor.getValue();
                org.junit.jupiter.api.Assertions.assertNotNull(payload.get("findings"), "findings must not be null in complete event");
                org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) payload.get("findings")).size());
                org.junit.jupiter.api.Assertions.assertEquals("<html>findings report</html>", payload.get("html_report"));
                org.junit.jupiter.api.Assertions.assertEquals(true, payload.get("report_available"));
                org.junit.jupiter.api.Assertions.assertEquals(42L, payload.get("repositoryId"));
        }
}
