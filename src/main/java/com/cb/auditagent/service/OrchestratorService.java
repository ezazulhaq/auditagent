package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class OrchestratorService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ScannerService scannerService;
    private final LlmService llmService;
    private final FilePatchService filePatchService;
    private final ReporterService reporterService;
    private final DatabaseService databaseService;
    private final ConversationMemoryService memoryService;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory state tracking for active user sessions/threads
    private final Map<String, ThreadState> threadStates = new ConcurrentHashMap<>();

    private static class ThreadState {
        String repoPath;
        List<Vulnerability> findings = new ArrayList<>();
        ScanMetadata metadata = new ScanMetadata();
        Vulnerability activeVuln;
        int activeVulnIndex = -1;
        String activeRunId;
        volatile long lastAccessedEpochMs = System.currentTimeMillis();
    }

    public OrchestratorService(
            ScannerService scannerService,
            LlmService llmService,
            FilePatchService filePatchService,
            ReporterService reporterService,
            DatabaseService databaseService,
            ConversationMemoryService memoryService,
            AgentConfig agentConfig) {
        this.scannerService = scannerService;
        this.llmService = llmService;
        this.filePatchService = filePatchService;
        this.reporterService = reporterService;
        this.databaseService = databaseService;
        this.memoryService = memoryService;
        this.agentConfig = agentConfig;
    }

    private ThreadState getOrCreateThreadState(String threadId) {
        ThreadState state = threadStates.computeIfAbsent(threadId, k -> new ThreadState());
        state.lastAccessedEpochMs = System.currentTimeMillis();
        return state;
    }

    private void attachActiveVulnerability(ThreadState state, Vulnerability vulnerability) {
        state.activeVuln = vulnerability;
        state.activeVulnIndex = -1;
        for (int i = 0; i < state.findings.size(); i++) {
            if (state.findings.get(i).getId().equalsIgnoreCase(vulnerability.getId())) {
                state.activeVulnIndex = i;
                state.findings.set(i, vulnerability);
                break;
            }
        }
        if (state.activeVulnIndex < 0) {
            state.findings.add(vulnerability);
            state.activeVulnIndex = state.findings.size() - 1;
        }
    }

    /**
     * Evicts thread states that haven't been accessed in over 1 hour.
     * Called periodically via @Scheduled or manually.
     */
    @Scheduled(fixedRate = 3600000)
    public void evictStaleThreadStates() {
        long cutoff = System.currentTimeMillis() - 3_600_000; // 1 hour
        int before = threadStates.size();
        threadStates.entrySet().removeIf(e -> e.getValue().lastAccessedEpochMs < cutoff);
        int evicted = before - threadStates.size();
        if (evicted > 0) {
            logger.info("Evicted {} stale thread state(s), {} remaining", evicted, threadStates.size());
        }
    }

    private String formatSseData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Failed to serialize SSE event\"}";
        }
    }

    public Optional<Report> getCachedReport(String repoPath) {
        return databaseService.getReport(repoPath);
    }

    public Flux<String> scanRepo(String threadId, String targetRepo, String projectName, String scannerName,
            boolean forceRescan) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ThreadState state = getOrCreateThreadState(threadId);
        state.repoPath = targetRepo;

        // Run async in a worker thread
        Thread.startVirtualThread(() -> {
            try {
                // Check cache first
                if (!forceRescan) {
                    Optional<Report> cachedOpt = databaseService.getReport(targetRepo);
                    if (cachedOpt.isPresent()) {
                        Report cached = cachedOpt.get();
                        logger.info("Found cached report for repo: {}", targetRepo);

                        // Seed thread state
                        state.findings = cached.getFindings();
                        state.metadata = cached.getMetadata();

                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "cached");
                        response.put("html_report", cached.getHtmlReport());
                        response.put("message", "📦 Found existing report for `" + targetRepo
                                + "` in local database. Loading cached results...\n\n**To fix an issue, reply with its ID (e.g., VULN-123456)**");
                        sink.tryEmitNext(formatSseData(response));
                        sink.tryEmitComplete();
                        return;
                    }
                }

                // New Scan
                Map<String, Object> progress = new HashMap<>();
                progress.put("type", "progress");
                progress.put("step", "init");
                progress.put("progress", 5);

                File dir = new File(targetRepo);
                String initMsg = dir.isDirectory()
                        ? "🔍 Initializing Security Engine... Scanning Local Repository."
                        : "🔍 Initializing Security Engine... Cloning & Scanning Repository.";

                progress.put("message", initMsg);
                sink.tryEmitNext(formatSseData(progress));

                long startTime = System.currentTimeMillis();
                String startTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // Execute Semgrep with progress feedback
                ScannerService.ScanResult scanResult = scannerService.scan(targetRepo, (pMap) -> {
                    Map<String, Object> pEvent = new HashMap<>();
                    pEvent.put("type", "progress");
                    pEvent.put("step", pMap.get("step"));
                    pEvent.put("progress", pMap.get("progress"));
                    pEvent.put("message", pMap.get("message"));
                    sink.tryEmitNext(formatSseData(pEvent));
                });

                long endTime = System.currentTimeMillis();
                String endTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                double durationSec = (endTime - startTime) / 1000.0;

                // Build metadata
                ScanMetadata metadata = new ScanMetadata();
                metadata.setProjectName(projectName);
                metadata.setScannerName(scannerName);
                metadata.setStartTime(startTimeStr);
                metadata.setEndTime(endTimeStr);
                metadata.setScanDuration(String.format("%.2f seconds", durationSec));
                metadata.setTotalFilesScanned(scanResult.getFilesScanned());
                metadata.setTotalLocScanned(scanResult.getTotalLoc());

                // Update Thread State
                state.findings = scanResult.getFindings();
                state.metadata = metadata;

                // Generate Reports
                String mdReport = reporterService.generateMarkdown(state.findings, metadata, targetRepo);
                String htmlReport = reporterService.generateHtml(state.findings, metadata, targetRepo);

                // Save to DB
                databaseService.saveReport(targetRepo, mdReport, htmlReport, state.findings, metadata);

                // Emit Complete
                Map<String, Object> complete = new HashMap<>();
                complete.put("type", "complete");
                complete.put("html_report", htmlReport);
                complete.put("findings", state.findings);
                complete.put("metadata", metadata);

                String completeMsg = String.format(
                        "✅ Scan complete! Found %d issues in %d files. **To fix an issue, reply with its ID (e.g., VULN-123456)**",
                        state.findings.size(), metadata.getTotalFilesScanned());
                if (scanResult.getErrorCount() > 0) {
                    completeMsg += String.format(" (⚠️ %d parser warnings occurred during scan)",
                            scanResult.getErrorCount());
                }
                complete.put("message", completeMsg);

                sink.tryEmitNext(formatSseData(complete));
                sink.tryEmitComplete();

            } catch (Exception e) {
                logger.error("Error during repo scan orchestration", e);
                Map<String, Object> error = new HashMap<>();
                error.put("type", "status");
                error.put("message", "❌ Scan failed due to exception: " + e.getMessage());
                sink.tryEmitNext(formatSseData(error));
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public Flux<String> analyzeVulnerability(String threadId, String vulnId, String targetRepo) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ThreadState state = getOrCreateThreadState(threadId);

        Thread.startVirtualThread(() -> {
            Vulnerability vuln = null; // Declared outside try so catch can access it (Fix #8)
            try {
                databaseService.getReport(targetRepo).ifPresent(report -> {
                    state.repoPath = report.getRepoPath();
                    state.findings = new ArrayList<>(report.getFindings());
                    state.metadata = report.getMetadata();
                });
                // Fetch vulnerability from DB to verify
                vuln = databaseService.getVulnerabilityById(vulnId, targetRepo).orElse(null);
                if (vuln == null) {
                    // Try to find it in active findings list
                    vuln = state.findings.stream()
                            .filter(v -> v.getId().equalsIgnoreCase(vulnId))
                            .findFirst()
                            .orElse(null);
                }

                if (vuln == null) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("type", "status");
                    err.put("message", "⚠️ Could not find vulnerability `" + vulnId
                            + "` in the database. Please run a scan first.");
                    sink.tryEmitNext(formatSseData(err));
                    sink.tryEmitComplete();
                    return;
                }

                attachActiveVulnerability(state, vuln);

                // Set initial status
                vuln.setStatus(VulnerabilityStatus.ANALYZING);
                databaseService.updateVulnerabilityStatus(vuln.getId(), VulnerabilityStatus.ANALYZING,
                        vuln.getProposedFix());

                databaseService.ensureMemoryThread(threadId, "legacy-local", targetRepo);
                AgentRunRecord run = databaseService.createAgentRun(
                        threadId, vuln, targetRepo, agentConfig.getMaxIterations());
                state.activeRunId = run.runId();

                // Run the autonomous agent loop
                LlmService.AgentResult result = llmService.runAgentLoop(run.runId(), threadId, vuln, targetRepo,
                        false, 0, (eventMap) -> {
                            // Stream intermediate events (tool calls, thinking) directly to the client
                            eventMap.put("runId", run.runId());
                            sink.tryEmitNext(formatSseData(eventMap));
                        });

                // Update status based on agent result
                // Gap #7: Use isPatchApplied() for accurate status
                if (result.isVerified()) {
                    vuln.setStatus(VulnerabilityStatus.AWAITING_APPROVAL);
                } else {
                    vuln.setStatus(VulnerabilityStatus.PATCH_FAILED);
                }

                // Update active vulnerability with the agent's final summary
                vuln.setProposedFix(result.getFinalResponse());
                databaseService.updateVulnerabilityStatus(vuln.getId(), vuln.getStatus(), result.getFinalResponse());

                // Build detailed final response
                Map<String, Object> response = new HashMap<>();
                response.put("type", "analysis_complete");
                response.put("requires_approval", vuln.getStatus() == VulnerabilityStatus.AWAITING_APPROVAL);
                response.put("findings", state.findings);
                response.put("runId", run.runId());

                String approvalPrompt;
                if (vuln.getStatus() == VulnerabilityStatus.AWAITING_APPROVAL) {
                    approvalPrompt = "**Do you approve this fix? (yes/no)**";
                } else {
                    approvalPrompt = "**Agent could not finalize a verified fix. Please review manually.**";
                }

                String fixMsg = String.format(
                        "**🚨 Issue Analysis:** %s [%s]\n\n" +
                                "**Agent Summary:**\n%s\n\n" +
                                "%s",
                        vuln.getVulnType(), vuln.getId(),
                        result.getFinalResponse(),
                        approvalPrompt);
                response.put("message", fixMsg);

                sink.tryEmitNext(formatSseData(response));
                sink.tryEmitComplete();

            } catch (Exception e) {
                logger.error("Error during vulnerability analysis orchestration", e);
                // Fix #8: Update vuln status so it doesn't stay in ANALYZING forever
                try {
                    if (vuln != null) {
                        vuln.setStatus(VulnerabilityStatus.PATCH_FAILED);
                        databaseService.updateVulnerabilityStatus(vuln.getId(), VulnerabilityStatus.PATCH_FAILED,
                                "Agent error: " + e.getMessage());
                    }
                    if (state.activeRunId != null) {
                        databaseService.getAgentRun(state.activeRunId)
                                .ifPresent(run -> databaseService.updateAgentRun(run.runId(), AgentPhase.FAILED,
                                        AgentRunStatus.FAILED,
                                        run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                                        run.rescanPassed(), run.testsPassed(), run.checkpointJson(), run.finalSummary(),
                                        "ORCHESTRATION_ERROR", e.getMessage()));
                    }
                } catch (Exception dbErr) {
                    logger.error("Failed to update vuln status after error", dbErr);
                }
                Map<String, Object> err = new HashMap<>();
                err.put("type", "status");
                err.put("message", "❌ Analysis failed: " + e.getMessage());
                sink.tryEmitNext(formatSseData(err));
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public Flux<String> approveFix(String threadId, String targetRepo, String decision) {
        return approveFix(threadId, targetRepo, null, null, decision);
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public Flux<String> approveFix(String threadId, String targetRepo, String vulnId,
            String runId, String decision) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ThreadState state = getOrCreateThreadState(threadId);

        Thread.startVirtualThread(() -> {
            try {
                if (runId == null || runId.isBlank() || vulnId == null || vulnId.isBlank()) {
                    throw new IllegalArgumentException("runId and vulnId are required for approval.");
                }
                AgentRunRecord run = databaseService.getAgentRun(runId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown run: " + runId));
                if (!run.threadId().equals(threadId)
                        || !databaseService.normalizePath(targetRepo).equals(run.repoPath())
                        || !run.vulnerabilityId().equalsIgnoreCase(vulnId)) {
                    throw new IllegalArgumentException(
                            "Run does not match the requested thread, repository, or vulnerability.");
                }
                databaseService.getReport(targetRepo).ifPresent(report -> {
                    state.repoPath = report.getRepoPath();
                    state.findings = new ArrayList<>(report.getFindings());
                    state.metadata = report.getMetadata();
                });
                Vulnerability vuln = databaseService.getVulnerabilityById(
                        run.vulnerabilityId(), run.repoPath()).orElse(null);
                if (vuln == null) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("type", "status");
                    err.put("message",
                            "❌ Cannot apply fix: no active vulnerability in memory context. Please select a vulnerability to analyze first.");
                    sink.tryEmitNext(formatSseData(err));
                    sink.tryEmitComplete();
                    return;
                }
                attachActiveVulnerability(state, vuln);
                if (run.status() != AgentRunStatus.AWAITING_APPROVAL) {
                    throw new IllegalStateException("Run is not awaiting approval: " + run.status());
                }
                if (!("APPROVE".equalsIgnoreCase(decision) || "YES".equalsIgnoreCase(decision)
                        || "REJECT".equalsIgnoreCase(decision) || "NO".equalsIgnoreCase(decision))) {
                    throw new IllegalArgumentException("Decision must be APPROVE or REJECT.");
                }

                Map<String, Object> status = new HashMap<>();
                status.put("type", "status");
                status.put("message", String.format("Decision recorded: %s. Processing changes...", decision));
                sink.tryEmitNext(formatSseData(status));

                String statusMsg;
                if ("APPROVE".equalsIgnoreCase(decision) || "YES".equalsIgnoreCase(decision)) {
                    // The agent already applied the patch and verified it! We just commit the
                    // status.
                    vuln.setStatus(VulnerabilityStatus.FIXED);
                    databaseService.updateAgentRun(run.runId(), AgentPhase.APPROVED, AgentRunStatus.APPROVED,
                            run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                            run.rescanPassed(), run.testsPassed(), run.checkpointJson(), vuln.getProposedFix(), null,
                            null);
                    databaseService.saveApprovedRepositoryMemory(targetRepo, vuln, run.runId(), vuln.getProposedFix());
                    statusMsg = String.format(
                            "✅ Fix approved for issue [%s]. The changes applied by the agent are kept.", vuln.getId());
                } else {
                    boolean rolledBack = rollbackRunChanges(run, targetRepo, vuln);
                    vuln.setStatus(VulnerabilityStatus.DETECTED);
                    databaseService.updateAgentRun(run.runId(), AgentPhase.REJECTED, AgentRunStatus.REJECTED,
                            run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                            run.rescanPassed(), run.testsPassed(), run.checkpointJson(), run.finalSummary(), null,
                            null);
                    statusMsg = String.format("❌ Fix rejected for issue [%s]. Status restored to DETECTED. %s",
                            vuln.getId(),
                            rolledBack ? "File has been successfully rolled back to its original state."
                                    : "⚠️ Warning: Rollback failed. Manual review required.");
                }

                // Update Database
                databaseService.updateVulnerabilityStatus(vuln.getId(), vuln.getStatus(), vuln.getProposedFix());

                // Update findings list inside thread state
                if (state.activeVulnIndex != -1 && state.activeVulnIndex < state.findings.size()) {
                    state.findings.set(state.activeVulnIndex, vuln);
                }

                // Regenerate reports
                String mdReport = reporterService.generateMarkdown(state.findings, state.metadata, targetRepo);
                String htmlReport = reporterService.generateHtml(state.findings, state.metadata, targetRepo);

                // Save report updates to DB
                databaseService.saveReport(targetRepo, mdReport, htmlReport, state.findings, state.metadata);

                // Clear context for next issue
                state.activeVuln = null;
                state.activeVulnIndex = -1;
                state.activeRunId = null;

                // Durable memory is retained for restoration and audit.
                memoryService.clearHistory(threadId);

                // Yield Complete
                Map<String, Object> complete = new HashMap<>();
                complete.put("type", "complete");
                complete.put("html_report", htmlReport);
                complete.put("findings", state.findings);
                complete.put("metadata", state.metadata);
                complete.put("message", statusMsg + " Report has been successfully updated!");

                sink.tryEmitNext(formatSseData(complete));
                sink.tryEmitComplete();

            } catch (Exception e) {
                logger.error("Error during fix approval orchestration", e);
                Map<String, Object> err = new HashMap<>();
                err.put("type", "status");
                err.put("message", "❌ Approval processing failed: " + e.getMessage());
                sink.tryEmitNext(formatSseData(err));
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    public Flux<String> resumeRun(String threadId, String runId) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.startVirtualThread(() -> {
            try {
                AgentRunRecord run = databaseService.getAgentRun(runId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown run: " + runId));
                if (!run.threadId().equals(threadId))
                    throw new IllegalArgumentException("Run belongs to a different thread.");
                if (run.status() != AgentRunStatus.INTERRUPTED) {
                    throw new IllegalStateException("Only interrupted runs can be resumed.");
                }
                for (RunChangeRecord change : databaseService.getRunChanges(runId)) {
                    if ("APPLIED".equals(change.state())
                            && !Objects.equals(change.afterHash(), hashFile(run.repoPath(), change.filePath()))) {
                        databaseService.updateAgentRun(runId, AgentPhase.CONFLICTED, AgentRunStatus.CONFLICTED,
                                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(),
                                run.rescanPassed(),
                                run.testsPassed(), run.checkpointJson(), run.finalSummary(), "FILE_HASH_CONFLICT",
                                "File changed after the checkpoint: " + change.filePath());
                        throw new IllegalStateException(
                                "Resume blocked because a patched file changed: " + change.filePath());
                    }
                }
                Vulnerability vuln = databaseService.getVulnerabilityById(run.vulnerabilityId(), run.repoPath())
                        .orElseThrow(() -> new IllegalStateException("Run vulnerability no longer exists."));
                ThreadState state = getOrCreateThreadState(threadId);
                databaseService.getReport(run.repoPath()).ifPresent(report -> {
                    state.findings = report.getFindings();
                    state.metadata = report.getMetadata();
                });
                attachActiveVulnerability(state, vuln);
                state.activeRunId = runId;
                state.repoPath = run.repoPath();

                LlmService.AgentResult result = llmService.runAgentLoop(runId, threadId, vuln, run.repoPath(),
                        true, run.iteration(), event -> {
                            event.put("runId", runId);
                            sink.tryEmitNext(formatSseData(event));
                        });
                vuln.setProposedFix(result.getFinalResponse());
                vuln.setStatus(
                        result.isVerified() ? VulnerabilityStatus.AWAITING_APPROVAL : VulnerabilityStatus.PATCH_FAILED);
                databaseService.updateVulnerabilityStatus(vuln.getId(), vuln.getStatus(), vuln.getProposedFix());
                Map<String, Object> complete = new LinkedHashMap<>();
                complete.put("type", "analysis_complete");
                complete.put("runId", runId);
                complete.put("requires_approval", result.isVerified());
                complete.put("message", result.getFinalResponse() + (result.isVerified()
                        ? "\n\n**Do you approve this fix? (yes/no)**"
                        : "\n\nThe resumed run did not produce a verified fix."));
                sink.tryEmitNext(formatSseData(complete));
                sink.tryEmitComplete();
            } catch (Exception e) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("type", "status");
                error.put("runId", runId);
                error.put("runStatus", databaseService.getAgentRun(runId)
                        .map(run -> run.status().name()).orElse("FAILED"));
                error.put("message", "Resume failed: " + e.getMessage());
                sink.tryEmitNext(formatSseData(error));
                sink.tryEmitComplete();
            }
        });
        return sink.asFlux();
    }

    public Map<String, Object> discardRun(String threadId, String runId) {
        AgentRunRecord run = databaseService.getAgentRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown run: " + runId));
        if (!run.threadId().equals(threadId))
            throw new IllegalArgumentException("Run belongs to a different thread.");
        Vulnerability vuln = databaseService.getVulnerabilityById(run.vulnerabilityId(), run.repoPath()).orElse(null);
        boolean rolledBack = rollbackRunChanges(run, run.repoPath(), vuln);
        databaseService.updateAgentRun(runId, AgentPhase.DISCARDED, AgentRunStatus.DISCARDED,
                run.iteration(), run.retryCount(), run.patchApplied(), run.compilePassed(), run.rescanPassed(),
                run.testsPassed(), run.checkpointJson(), run.finalSummary(), null, null);
        if (vuln != null)
            databaseService.updateVulnerabilityStatus(vuln.getId(), VulnerabilityStatus.DETECTED,
                    vuln.getProposedFix());
        return Map.of("runId", runId, "status", "DISCARDED", "rolledBack", rolledBack);
    }

    private boolean rollbackRunChanges(AgentRunRecord run, String repoPath, Vulnerability vuln) {
        if (run == null)
            return vuln != null && filePatchService.rollback(repoPath, vuln.getFilePath());
        List<RunChangeRecord> changes = new ArrayList<>(databaseService.getRunChanges(run.runId()));
        if (changes.isEmpty())
            return true;
        boolean allRolledBack = true;
        for (RunChangeRecord change : changes) {
            if (!"APPLIED".equals(change.state()))
                continue;
            boolean restored = filePatchService.restoreBackup(repoPath, change.filePath(), change.backupPath());
            allRolledBack &= restored;
            if (restored)
                databaseService.markRunChangeState(change.changeId(), "ROLLED_BACK");
        }
        return allRolledBack;
    }

    private String hashFile(String repoPath, String filePath) {
        try {
            Path root = Path.of(repoPath).toAbsolutePath().normalize();
            Path file = root.resolve(filePath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file))
                return null;
            byte[] bytes = Files.readString(file, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}
