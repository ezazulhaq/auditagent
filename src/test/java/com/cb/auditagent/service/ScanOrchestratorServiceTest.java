package com.cb.auditagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.ReporterService;
import com.cb.auditagent.service.RepositoryAccessService;
import com.cb.auditagent.service.ScanExecutionException;
import com.cb.auditagent.service.ScanOrchestratorService;
import com.cb.auditagent.service.ScanTimeoutException;
import com.cb.auditagent.service.ScannerService;
import com.cb.auditagent.service.SourceControlProvider;

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
}
