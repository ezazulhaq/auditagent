package com.cb.auditagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanOrchestratorService {
    private static final Logger logger = LoggerFactory.getLogger(ScanOrchestratorService.class);

    private final DatabaseService database;
    private final SourceControlProvider sourceControl;
    private final GitHubApiClient github;
    private final ScannerService scanner;
    private final ReporterService reporter;
    private final RepositoryAccessService repositoryAccess;

    public ScanOrchestratorService(
            DatabaseService database,
            SourceControlProvider sourceControl,
            GitHubApiClient github,
            ScannerService scanner,
            ReporterService reporter,
            RepositoryAccessService repositoryAccess) {
        this.database = database;
        this.sourceControl = sourceControl;
        this.github = github;
        this.scanner = scanner;
        this.reporter = reporter;
        this.repositoryAccess = repositoryAccess;
    }

    public Flux<String> scan(AuthenticatedUser user, String threadId, long repositoryId, String branch,
            String scannerName, boolean forceRescan) {
        requireThreadOwner(threadId, user);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            Path workspace = null;
            try {
                ManagedRepository repository = repositoryAccess.requireRepositoryAccess(user, repositoryId);
                String installationToken = github.installationToken(repository.installationId());
                String baseSha = sourceControl.branchHead(repository, branch, installationToken);
                String reportKey = reportKey(repositoryId, branch);
                Optional<ScanSnapshot> latest = database.findLatestScanSnapshot(repositoryId, branch);
                if (!forceRescan && latest.filter(snapshot -> snapshot.baseSha().equals(baseSha)).isPresent()) {
                    Optional<Report> cached = database.getReport(reportKey);
                    if (cached.isPresent()) {
                        emit(sink, resultEvent("cached", cached.get(), repository, branch, baseSha,
                                "Loaded the scan for the current GitHub commit."));
                        sink.tryEmitComplete();
                        return;
                    }
                }

                emit(sink, Map.of("type", "progress", "step", "init", "progress", 5,
                        "message", "Preparing an isolated GitHub workspace at " + shortSha(baseSha) + "..."));
                String workspaceKey = "scan-" + UUID.randomUUID();
                workspace = sourceControl.cloneAtCommit(repository, branch, baseSha, installationToken, workspaceKey);
                emit(sink, Map.of("type", "workspace_prepared", "repository", repository.fullName(),
                        "branch", branch, "baseSha", baseSha));

                long started = System.currentTimeMillis();
                ScannerService.ScanResult scanResult = scanner.scan(workspace.toString(), progress -> {
                    Map<String, Object> event = new LinkedHashMap<>(progress);
                    event.put("type", "progress");
                    emit(sink, event);
                });
                ScanMetadata metadata = new ScanMetadata();
                metadata.setProjectName(repository.fullName());
                metadata.setScannerName(scannerName);
                metadata.setStartTime(java.time.Instant.ofEpochMilli(started).toString());
                metadata.setEndTime(java.time.Instant.now().toString());
                metadata.setScanDuration(
                        String.format("%.2f seconds", (System.currentTimeMillis() - started) / 1000.0));
                metadata.setTotalFilesScanned(scanResult.getFilesScanned());
                metadata.setTotalLocScanned(scanResult.getTotalLoc());
                List<Vulnerability> findings = scanResult.getFindings();
                String markdown = reporter.generateMarkdown(findings, metadata, repository.fullName());
                String html = reporter.generateHtml(findings, metadata, repository.fullName());
                database.saveReport(reportKey, markdown, html, findings, metadata);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                database.saveScanSnapshot(new ScanSnapshot(UUID.randomUUID().toString(), repositoryId, branch,
                        baseSha, reportKey, workspace.toString(), user.userId(), now));

                int high = 0, medium = 0, low = 0, info = 0;
                for (Vulnerability v : findings) {
                    String sev = v.getSeverity() != null ? v.getSeverity().name().toUpperCase() : "INFO";
                    if ("HIGH".equals(sev) || "CRITICAL".equals(sev))
                        high++;
                    else if ("MEDIUM".equals(sev))
                        medium++;
                    else if ("LOW".equals(sev))
                        low++;
                    else
                        info++;
                }
                database.saveScanHistory(new ScanHistoryRecord(UUID.randomUUID().toString(), repositoryId, branch,
                        baseSha, now, findings.size(), high, medium, low, info));

                Report report = new Report(reportKey, markdown, html, findings, metadata);
                emit(sink, resultEvent("complete", report, repository, branch, baseSha,
                        "Scan complete at " + shortSha(baseSha) + ". Found " + findings.size() + " issue(s)."));
                sink.tryEmitComplete();
            } catch (Exception e) {
                logger.error("Managed GitHub scan failed: {}", safeMessage(e));
                String code = e instanceof ScanTimeoutException ? "SCAN_TIMEOUT" : "SCAN_FAILED";
                emit(sink, Map.of("type", "error", "code", code, "message", safeMessage(e)));
                sink.tryEmitComplete();
            } finally {
                if (workspace != null)
                    safeCleanup(workspace);
            }
        });
        return sink.asFlux();
    }

    public java.util.Map<String, Object> report(AuthenticatedUser user, long repositoryId, String branch) {
        ManagedRepository repository = repositoryAccess.requireRepositoryAccess(user, repositoryId);
        ScanSnapshot snapshot = database.findLatestScanSnapshot(repositoryId, branch)
                .orElseThrow(() -> new IllegalArgumentException("No scan exists for this repository branch"));
        Report report = database.getReport(snapshot.reportKey())
                .orElseThrow(() -> new IllegalStateException("The scan report is unavailable"));
        return resultEvent("report", report, repository, branch, snapshot.baseSha(),
                "Restored the latest pinned scan.");
    }

    public ReportArtifact exportReport(AuthenticatedUser user, long repositoryId, String branch, String format) {
        ManagedRepository repository = repositoryAccess.requireRepositoryAccess(user, repositoryId);
        ScanSnapshot snapshot = database.findLatestScanSnapshot(repositoryId, branch)
                .orElseThrow(() -> new IllegalArgumentException("No scan exists for this repository branch"));
        Report report = database.getReport(snapshot.reportKey())
                .orElseThrow(() -> new IllegalStateException("The scan report is unavailable"));
        String baseName = safeArtifactName(repository.name() + "-" + branch + "-security-report");

        return switch (Optional.ofNullable(format).orElse("").trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pdf" -> new ReportArtifact(
                    reporter.generatePdf(report.getFindings(), report.getMetadata(), repository.fullName(),
                            branch, snapshot.baseSha()),
                    "application/pdf", baseName + ".pdf");
            case "markdown", "md" -> new ReportArtifact(reporter.generateMarkdown(report.getFindings(),
                    report.getMetadata(), repository.fullName(), branch, snapshot.baseSha())
                    .getBytes(StandardCharsets.UTF_8), "text/markdown; charset=UTF-8", baseName + ".md");
            default -> throw new IllegalArgumentException("format must be pdf or markdown");
        };
    }

    private void requireThreadOwner(String threadId, AuthenticatedUser user) {
        if (threadId == null || !database.memoryThreadBelongsTo(threadId, user.userId()))
            throw new SecurityException("Conversation does not belong to the authenticated user");
    }

    private String reportKey(long repositoryId, String branch) {
        return "github:" + repositoryId + ":" + branch;
    }

    private String shortSha(String sha) {
        return sha == null ? "unknown" : sha.substring(0, Math.min(12, sha.length()));
    }

    private Map<String, Object> resultEvent(String type, Report report, ManagedRepository repository,
            String branch, String baseSha, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("repositoryId", repository.repositoryId());
        result.put("repository", repository.fullName());
        result.put("branch", branch);
        result.put("baseSha", baseSha);
        result.put("report_available", true);
        result.put("html_report", report.getHtmlReport());
        result.put("findings", report.getFindings());
        result.put("metadata", report.getMetadata());
        result.put("message", message);
        return result;
    }

    private String safeArtifactName(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
        normalized = normalized.replaceAll("^[.-]+|[.-]+$", "");
        if (normalized.length() > 120)
            normalized = normalized.substring(0, 120).replaceAll("[.-]+$", "");
        return normalized.isBlank() ? "auditagent-security-report" : normalized;
    }

    private void emit(Sinks.Many<String> sink, Map<String, ?> event) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            sink.tryEmitNext(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            sink.tryEmitNext("{\"type\":\"status\",\"message\":\"Event serialization failed\"}");
        }
    }

    private void safeCleanup(Path path) {
        try {
            sourceControl.cleanupWorkspace(path);
        } catch (Exception e) {
            logger.warn("Managed workspace cleanup failed: {}", safeMessage(e));
        }
    }

    private String safeMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null &&
                (cause instanceof java.util.concurrent.ExecutionException ||
                        cause instanceof java.lang.reflect.InvocationTargetException ||
                        cause instanceof RuntimeException)) {
            if (cause.getCause() == cause)
                break;
            cause = cause.getCause();
        }
        String message = Optional.ofNullable(cause.getMessage()).orElse(cause.getClass().getSimpleName());
        if (message.contains("STALE_BASE")) {
            return "The repository branch has advanced since the last scan (e.g., a PR was merged). Please run a fresh scan before analyzing further findings.";
        }
        // Very basic redaction if needed, but normally handled by MemoryRedactor.
        // We will just return the message since redactor is not here.
        if (message.length() > 500) {
            return message.substring(0, 500) + "...";
        }
        return message;
    }
}
