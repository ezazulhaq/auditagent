package com.cb.auditagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.entity.UploadedProject;
import com.cb.auditagent.repository.UploadedProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectUploadServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser("github:42", 42, "tester", "Tester", "", "csrf-123");
    private final AuthenticatedUser otherUser = new AuthenticatedUser("github:99", 99, "other", "Other", "", "csrf-456");

    private ZipArchiveExtractor extractor;
    private ScannerService scanner;
    private ReporterService reporter;
    private DatabaseService database;
    private UploadedProjectRepository uploadedProjectRepository;
    private ProjectUploadService service;

    @BeforeEach
    void setUp() {
        extractor = mock(ZipArchiveExtractor.class);
        scanner = mock(ScannerService.class);
        reporter = mock(ReporterService.class);
        database = mock(DatabaseService.class);
        uploadedProjectRepository = mock(UploadedProjectRepository.class);
        service = new ProjectUploadService(extractor, scanner, reporter, database, uploadedProjectRepository, new ObjectMapper());
    }

    @Test
    void testGetUploadReportSuccess() {
        String uploadId = "upload-123";
        UploadedProject project = UploadedProject.builder()
                .uploadId(uploadId)
                .userId(user.userId())
                .projectName("demo-service")
                .originalFileName("demo-service.zip")
                .reportKey("upload:" + uploadId)
                .fileSizeBytes(1024)
                .totalFindings(0)
                .createdAt(LocalDateTime.now())
                .build();

        Report report = new Report("upload:" + uploadId, "# Markdown", "<div>Html</div>", List.of(), new ScanMetadata());

        when(uploadedProjectRepository.findByUploadIdAndUserId(uploadId, user.userId()))
                .thenReturn(Optional.of(project));
        when(database.getReport("upload:" + uploadId)).thenReturn(Optional.of(report));

        Map<String, Object> result = service.getUploadReport(user, uploadId);

        assertEquals("report", result.get("type"));
        assertEquals("upload", result.get("source"));
        assertEquals(uploadId, result.get("uploadId"));
        assertEquals("demo-service", result.get("projectName"));
        assertEquals("<div>Html</div>", result.get("html_report"));
    }

    @Test
    void testGetUploadReportUnauthorizedForOtherUser() {
        String uploadId = "upload-123";
        when(uploadedProjectRepository.findByUploadIdAndUserId(uploadId, otherUser.userId()))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getUploadReport(otherUser, uploadId));
    }

    @Test
    void testExportUploadReportPdf() {
        String uploadId = "upload-123";
        UploadedProject project = UploadedProject.builder()
                .uploadId(uploadId)
                .userId(user.userId())
                .projectName("demo-service")
                .originalFileName("demo-service.zip")
                .reportKey("upload:" + uploadId)
                .fileSizeBytes(1024)
                .totalFindings(0)
                .createdAt(LocalDateTime.now())
                .build();

        Report report = new Report("upload:" + uploadId, "# Markdown", "<div>Html</div>", List.of(), new ScanMetadata());

        when(uploadedProjectRepository.findByUploadIdAndUserId(uploadId, user.userId()))
                .thenReturn(Optional.of(project));
        when(database.getReport("upload:" + uploadId)).thenReturn(Optional.of(report));
        when(reporter.generatePdf(any(), any(), eq("demo-service"), eq("manual-upload"), any()))
                .thenReturn("PDF_BYTES".getBytes(StandardCharsets.UTF_8));

        ReportArtifact artifact = service.exportUploadReport(user, uploadId, "pdf");

        assertNotNull(artifact);
        assertEquals("application/pdf", artifact.contentType());
        assertEquals("demo-service-security-report.pdf", artifact.fileName());
    }

    @Test
    void testExportUploadReportMarkdown() {
        String uploadId = "upload-123";
        UploadedProject project = UploadedProject.builder()
                .uploadId(uploadId)
                .userId(user.userId())
                .projectName("demo-service")
                .originalFileName("demo-service.zip")
                .reportKey("upload:" + uploadId)
                .fileSizeBytes(1024)
                .totalFindings(0)
                .createdAt(LocalDateTime.now())
                .build();

        Report report = new Report("upload:" + uploadId, "# Markdown", "<div>Html</div>", List.of(), new ScanMetadata());

        when(uploadedProjectRepository.findByUploadIdAndUserId(uploadId, user.userId()))
                .thenReturn(Optional.of(project));
        when(database.getReport("upload:" + uploadId)).thenReturn(Optional.of(report));
        when(reporter.generateMarkdown(any(), any(), eq("demo-service"), eq("manual-upload"), any()))
                .thenReturn("# Generated Markdown");

        ReportArtifact artifact = service.exportUploadReport(user, uploadId, "markdown");

        assertNotNull(artifact);
        assertEquals("text/markdown; charset=UTF-8", artifact.contentType());
        assertEquals("demo-service-security-report.md", artifact.fileName());
    }
}

