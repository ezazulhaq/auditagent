package com.cb.auditagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.ReporterService;
import com.cb.auditagent.service.RepositoryAccessService;
import com.cb.auditagent.service.ScanOrchestratorService;
import com.cb.auditagent.service.ScannerService;
import com.cb.auditagent.service.SourceControlProvider;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScanOrchestratorReportExportTest {
        private final AuthenticatedUser user = new AuthenticatedUser("github:99", 99, "octocat", "Octo", "", "csrf");
        private ReporterService reporter;
        private ScanOrchestratorService service;

        @BeforeEach
        void setUp() {
                DatabaseService database = mock(DatabaseService.class);
                SourceControlProvider source = mock(SourceControlProvider.class);
                GitHubAuthService auth = mock(GitHubAuthService.class);
                reporter = mock(ReporterService.class);
                ManagedRepository repository = new ManagedRepository(42, 9, "octo", "repo", "octo/repo",
                                "https://github.test/octo/repo.git", "main", true, "WRITE");
                ScanSnapshot snapshot = new ScanSnapshot("snapshot", 42, "feature/report", "base-sha",
                                "github:42:feature/report", "", user.userId(), LocalDateTime.now());
                ScanMetadata metadata = new ScanMetadata();
                Report report = new Report(snapshot.reportKey(), "# Markdown report", "<html>legacy</html>", List.of(),
                                metadata);
                when(database.getManagedRepository(42)).thenReturn(Optional.of(repository));
                when(auth.accessToken(user.userId())).thenReturn("user-token");
                when(source.userCanRead("user-token", repository)).thenReturn(true);
                when(database.findLatestScanSnapshot(42, "feature/report")).thenReturn(Optional.of(snapshot));
                when(database.getReport(snapshot.reportKey())).thenReturn(Optional.of(report));
                when(reporter.generatePdf(any(), any(), any(), any(), any()))
                                .thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
                when(reporter.generateMarkdown(any(), any(), any(), any(), any())).thenReturn("# Markdown report");
                RepositoryAccessService repoAccess = new RepositoryAccessService(database, source, auth);
                service = new ScanOrchestratorService(database, source, mock(GitHubApiClient.class),
                                mock(ScannerService.class), reporter, repoAccess);
        }

        @Test
        void exportsPdfAndMarkdownWithSafeEnterpriseFilenames() {
                ReportArtifact pdf = service.exportReport(user, 42, "feature/report", "pdf");
                ReportArtifact markdown = service.exportReport(user, 42, "feature/report", "markdown");

                assertEquals("application/pdf", pdf.contentType());
                assertEquals("repo-feature-report-security-report.pdf", pdf.fileName());
                assertArrayEquals("pdf".getBytes(StandardCharsets.UTF_8), pdf.content());
                assertEquals("text/markdown; charset=UTF-8", markdown.contentType());
                assertEquals("repo-feature-report-security-report.md", markdown.fileName());
                assertEquals("# Markdown report", new String(markdown.content(), StandardCharsets.UTF_8));
                verify(reporter).generatePdf(any(), any(), eq("octo/repo"), eq("feature/report"), eq("base-sha"));
                verify(reporter).generateMarkdown(any(), any(), eq("octo/repo"), eq("feature/report"), eq("base-sha"));
        }

        @Test
        void rejectsUnknownExportFormats() {
                assertThrows(IllegalArgumentException.class,
                                () -> service.exportReport(user, 42, "feature/report", "html"));
        }
}
