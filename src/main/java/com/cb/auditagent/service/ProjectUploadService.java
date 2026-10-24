package com.cb.auditagent.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.entity.UploadedProject;
import com.cb.auditagent.repository.UploadedProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class ProjectUploadService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectUploadService.class);

    private final ZipArchiveExtractor extractor;
    private final ScannerService scanner;
    private final ReporterService reporter;
    private final DatabaseService database;
    private final UploadedProjectRepository uploadedProjectRepository;
    private final ObjectMapper objectMapper;

    public ProjectUploadService(
            ZipArchiveExtractor extractor,
            ScannerService scanner,
            ReporterService reporter,
            DatabaseService database,
            UploadedProjectRepository uploadedProjectRepository,
            ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.scanner = scanner;
        this.reporter = reporter;
        this.database = database;
        this.uploadedProjectRepository = uploadedProjectRepository;
        this.objectMapper = objectMapper;
    }

    public Flux<String> scanUpload(AuthenticatedUser user, FilePart filePart, String rawProjectName, String scannerName) {
        if (filePart == null) {
            throw new IllegalArgumentException("A ZIP file is required for project upload");
        }

        String filename = filePart.filename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported for project upload");
        }

        String projectName = sanitizeProjectName(rawProjectName, filename);
        String uploadId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        Thread.startVirtualThread(() -> {
            Path tempZipFile = null;
            Path tempExtractDir = null;
            try {
                emit(sink, Map.of(
                        "type", "progress",
                        "step", "uploading",
                        "progress", 5,
                        "message", "Receiving project archive (" + filename + ")..."));

                tempZipFile = Files.createTempFile("auditagent-upload-", ".zip");
                filePart.transferTo(tempZipFile).block(Duration.ofSeconds(60));

                emit(sink, Map.of(
                        "type", "progress",
                        "step", "extracting",
                        "progress", 15,
                        "message", "Safely extracting project files..."));

                tempExtractDir = Files.createTempDirectory("auditagent-extract-" + uploadId);
                extractor.extract(tempZipFile, tempExtractDir);

                long started = System.currentTimeMillis();
                emit(sink, Map.of(
                        "type", "progress",
                        "step", "scanning",
                        "progress", 25,
                        "message", "Starting Semgrep static analysis..."));

                ScannerService.ScanResult scanResult = scanner.scan(tempExtractDir.toString(), progress -> {
                    Map<String, Object> event = new LinkedHashMap<>(progress);
                    event.put("type", "progress");
                    emit(sink, event);
                });

                ScanMetadata metadata = new ScanMetadata();
                metadata.setProjectName(projectName);
                metadata.setScannerName(Optional.ofNullable(scannerName).filter(s -> !s.isBlank()).orElse("semgrep"));
                metadata.setStartTime(java.time.Instant.ofEpochMilli(started).toString());
                metadata.setEndTime(java.time.Instant.now().toString());
                metadata.setScanDuration(String.format(Locale.ROOT, "%.2f seconds", (System.currentTimeMillis() - started) / 1000.0));
                metadata.setTotalFilesScanned(scanResult.getFilesScanned());
                metadata.setTotalLocScanned(scanResult.getTotalLoc());

                List<Vulnerability> findings = scanResult.getFindings();
                String shortId = uploadId.length() >= 8 ? uploadId.substring(0, 8) : uploadId;
                String markdown = reporter.generateMarkdown(findings, metadata, projectName, "manual-upload", shortId);
                String html = reporter.generateHtml(findings, metadata, projectName);

                String reportKey = "upload:" + uploadId;
                database.saveReport(reportKey, markdown, html, findings, metadata);

                UploadedProject project = UploadedProject.builder()
                        .uploadId(uploadId)
                        .userId(user.userId())
                        .projectName(projectName)
                        .originalFileName(filename)
                        .reportKey(reportKey)
                        .fileSizeBytes(Files.size(tempZipFile))
                        .totalFindings(findings.size())
                        .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build();
                uploadedProjectRepository.save(project);

                Map<String, Object> completeEvent = new LinkedHashMap<>();
                completeEvent.put("type", "complete");
                completeEvent.put("source", "upload");
                completeEvent.put("uploadId", uploadId);
                completeEvent.put("projectName", projectName);
                completeEvent.put("repository", projectName);
                completeEvent.put("branch", "manual-upload");
                completeEvent.put("baseSha", shortId);
                completeEvent.put("reportKey", reportKey);
                completeEvent.put("findings", findings);
                completeEvent.put("metadata", metadata);
                completeEvent.put("html_report", html);
                completeEvent.put("report_available", true);
                completeEvent.put("message", "Scan complete for uploaded project " + projectName + ". Found " + findings.size() + " issue(s).");
                emit(sink, completeEvent);

            } catch (Exception e) {
                logger.error("ZIP project upload scan failed: {}", e.getMessage(), e);
                emit(sink, Map.of(
                        "type", "error",
                        "code", "SCAN_FAILED",
                        "message", safeMessage(e)));
            } finally {
                if (tempExtractDir != null) {
                    extractor.deleteRecursively(tempExtractDir);
                }
                if (tempZipFile != null) {
                    try {
                        Files.deleteIfExists(tempZipFile);
                    } catch (Exception ex) {
                        logger.warn("Failed to delete temporary zip file: {}", ex.getMessage());
                    }
                }
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    public Map<String, Object> getUploadReport(AuthenticatedUser user, String uploadId) {
        UploadedProject project = uploadedProjectRepository.findByUploadIdAndUserId(uploadId, user.userId())
                .orElseThrow(() -> new NoSuchElementException("Uploaded project not found or access denied"));

        Report report = database.getReport(project.getReportKey())
                .orElseThrow(() -> new NoSuchElementException("The scan report is unavailable"));

        String shortId = uploadId.length() >= 8 ? uploadId.substring(0, 8) : uploadId;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "report");
        result.put("source", "upload");
        result.put("uploadId", uploadId);
        result.put("projectName", project.getProjectName());
        result.put("repository", project.getProjectName());
        result.put("branch", "manual-upload");
        result.put("baseSha", shortId);
        result.put("findings", report.getFindings());
        result.put("metadata", report.getMetadata());
        result.put("html_report", report.getHtmlReport());
        result.put("report_available", true);
        result.put("message", "Restored scan for uploaded project " + project.getProjectName());
        return result;
    }

    public ReportArtifact exportUploadReport(AuthenticatedUser user, String uploadId, String format) {
        UploadedProject project = uploadedProjectRepository.findByUploadIdAndUserId(uploadId, user.userId())
                .orElseThrow(() -> new NoSuchElementException("Uploaded project not found or access denied"));

        Report report = database.getReport(project.getReportKey())
                .orElseThrow(() -> new NoSuchElementException("The scan report is unavailable"));

        String shortId = uploadId.length() >= 8 ? uploadId.substring(0, 8) : uploadId;
        String baseName = safeArtifactName(project.getProjectName() + "-security-report");

        return switch (Optional.ofNullable(format).orElse("").trim().toLowerCase(Locale.ROOT)) {
            case "pdf" -> new ReportArtifact(
                    reporter.generatePdf(report.getFindings(), report.getMetadata(), project.getProjectName(),
                            "manual-upload", shortId),
                    "application/pdf", baseName + ".pdf");
            case "markdown", "md" -> new ReportArtifact(reporter.generateMarkdown(report.getFindings(),
                    report.getMetadata(), project.getProjectName(), "manual-upload", shortId)
                    .getBytes(StandardCharsets.UTF_8), "text/markdown; charset=UTF-8", baseName + ".md");
            default -> throw new IllegalArgumentException("format must be pdf or markdown");
        };
    }

    private void emit(Sinks.Many<String> sink, Map<String, Object> event) {
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            sink.tryEmitNext("{\"type\":\"status\",\"message\":\"Event serialization failed\"}");
        }
    }

    private String sanitizeProjectName(String rawProjectName, String filename) {
        if (rawProjectName != null && !rawProjectName.trim().isEmpty()) {
            return rawProjectName.trim().replaceAll("[^a-zA-Z0-9-_.]", "-");
        }
        String clean = filename;
        if (clean.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.trim().replaceAll("[^a-zA-Z0-9-_.]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return clean.isBlank() ? "uploaded-project" : clean;
    }

    private String safeArtifactName(String value) {
        String cleaned = (value == null ? "" : value).replaceAll("[^a-zA-Z0-9-_.]", "-").replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "auditagent-upload-report" : cleaned;
    }

    private String safeMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null &&
                (cause instanceof java.util.concurrent.ExecutionException ||
                        cause instanceof java.lang.reflect.InvocationTargetException ||
                        cause instanceof RuntimeException)) {
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        String message = Optional.ofNullable(cause.getMessage()).orElse(cause.getClass().getSimpleName());
        if (message.length() > 500) {
            return message.substring(0, 500) + "...";
        }
        return message;
    }
}

