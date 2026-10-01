package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private final MemoryConfig memoryConfig;
    private final DuckDbKnowledgeBaseService duckDbKnowledgeBaseService;
    private final VulnerabilityEntityRepository vulnerabilityEntityRepository;
    private final ReportRepository reportRepository;
    private final AgentRunRepository agentRunRepository;
    private final MemoryMessageRepository memoryMessageRepository;
    private final MemoryThreadRepository memoryThreadRepository;
    private final RepositoryMemoryRepository repositoryMemoryRepository;
    private final GlobalRemediationPatternRepository globalRemediationPatternRepository;
    private final RunChangeRepository runChangeRepository;
    private final VerificationResultRepository verificationResultRepository;
    private final AgentStepRepository agentStepRepository;
    private final OauthStateRepository oauthStateRepository;
    private final UserSessionRepository userSessionRepository;
    private final GithubAuthorizationRepository githubAuthorizationRepository;
    private final AppUserRepository appUserRepository;
    private final ManagedRepositoryRepository managedRepositoryRepository;
    private final ScanSnapshotRepository scanSnapshotRepository;
    private final RunPublicationRepository runPublicationRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ScanHistoryStatsRepository scanHistoryStatsRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final GraphCheckpointRepository graphCheckpointRepository;
    private final GraphNodeEventRepository graphNodeEventRepository;
    private final SubagentStateRepository subagentStateRepository;

    public DatabaseService(MemoryConfig memoryConfig, DuckDbKnowledgeBaseService duckDbKnowledgeBaseService,
            VulnerabilityEntityRepository vulnerabilityEntityRepository, ReportRepository reportRepository,
            AgentRunRepository agentRunRepository, MemoryMessageRepository memoryMessageRepository,
            MemoryThreadRepository memoryThreadRepository, RepositoryMemoryRepository repositoryMemoryRepository,
            GlobalRemediationPatternRepository globalRemediationPatternRepository,
            RunChangeRepository runChangeRepository, VerificationResultRepository verificationResultRepository,
            AgentStepRepository agentStepRepository, OauthStateRepository oauthStateRepository,
            UserSessionRepository userSessionRepository, GithubAuthorizationRepository githubAuthorizationRepository,
            AppUserRepository appUserRepository, ManagedRepositoryRepository managedRepositoryRepository,
            ScanSnapshotRepository scanSnapshotRepository, RunPublicationRepository runPublicationRepository,
            WebhookDeliveryRepository webhookDeliveryRepository, TokenUsageRepository tokenUsageRepository,
            ScanHistoryStatsRepository scanHistoryStatsRepository,
            UserNotificationRepository userNotificationRepository, GraphCheckpointRepository graphCheckpointRepository,
            GraphNodeEventRepository graphNodeEventRepository, SubagentStateRepository subagentStateRepository) {
        this.memoryConfig = memoryConfig;
        this.duckDbKnowledgeBaseService = duckDbKnowledgeBaseService;
        this.vulnerabilityEntityRepository = vulnerabilityEntityRepository;
        this.reportRepository = reportRepository;
        this.agentRunRepository = agentRunRepository;
        this.memoryMessageRepository = memoryMessageRepository;
        this.memoryThreadRepository = memoryThreadRepository;
        this.repositoryMemoryRepository = repositoryMemoryRepository;
        this.globalRemediationPatternRepository = globalRemediationPatternRepository;
        this.runChangeRepository = runChangeRepository;
        this.verificationResultRepository = verificationResultRepository;
        this.agentStepRepository = agentStepRepository;
        this.oauthStateRepository = oauthStateRepository;
        this.userSessionRepository = userSessionRepository;
        this.githubAuthorizationRepository = githubAuthorizationRepository;
        this.appUserRepository = appUserRepository;
        this.managedRepositoryRepository = managedRepositoryRepository;
        this.scanSnapshotRepository = scanSnapshotRepository;
        this.runPublicationRepository = runPublicationRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.scanHistoryStatsRepository = scanHistoryStatsRepository;
        this.userNotificationRepository = userNotificationRepository;
        this.graphCheckpointRepository = graphCheckpointRepository;
        this.graphNodeEventRepository = graphNodeEventRepository;
        this.subagentStateRepository = subagentStateRepository;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private int estimateTokens(String value) {
        if (value == null || value.isEmpty())
            return 1;
        return Math.max(1, value.length() / 4);
    }

    private String truncate(String value, int max) {
        if (value == null)
            return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private Vulnerability mapToVulnerability(com.cb.auditagent.entity.VulnerabilityEntity e) {
        if (e == null)
            return null;
        Vulnerability d = new Vulnerability();
        d.setId(e.getId());
        d.setFilePath(e.getFilePath());
        d.setLineNumber(e.getLineNumber());
        d.setCodeSnippet(e.getCodeSnippet());
        d.setSeverity(e.getSeverity() != null ? Severity.valueOf(e.getSeverity()) : Severity.MEDIUM);
        d.setVulnType(e.getVulnType());
        d.setDescription(e.getDescription());
        d.setRuleId(e.getRuleId());
        d.setFindingFingerprint(e.getFindingFingerprint());
        d.setLanguage(e.getLanguage());
        d.setProposedFix(e.getProposedFix());
        d.setStatus(e.getStatus() != null ? VulnerabilityStatus.valueOf(e.getStatus()) : VulnerabilityStatus.DETECTED);
        return d;
    }

    private Report mapToReport(com.cb.auditagent.entity.Report e) {
        if (e == null)
            return null;
        Report d = new Report();
        d.setRepoPath(e.getRepoPath());
        d.setMdReport(e.getMdReport());
        d.setHtmlReport(e.getHtmlReport());
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (e.getFindingsJson() != null && !e.getFindingsJson().trim().isEmpty()) {
                d.setFindings(mapper.readValue(e.getFindingsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Vulnerability>>() {
                        }));
            }
            if (e.getMetadataJson() != null && !e.getMetadataJson().trim().isEmpty()) {
                d.setMetadata(mapper.readValue(e.getMetadataJson(), ScanMetadata.class));
            } else {
                d.setMetadata(new ScanMetadata()); // Fallback for old scans
            }
        } catch (Exception ex) {
            logger.error("Failed to deserialize report metadata/findings", ex);
            d.setMetadata(new ScanMetadata());
        }
        return d;
    }

    private AgentRunRecord mapToAgentRunRecord(com.cb.auditagent.entity.AgentRun e) {
        if (e == null)
            return null;
        return new AgentRunRecord(e.getRunId(), e.getThreadId(), e.getVulnerabilityId(), e.getFindingFingerprint(),
                e.getRepoPath(), e.getPhase() != null ? AgentPhase.valueOf(e.getPhase()) : null,
                e.getStatus() != null ? AgentRunStatus.valueOf(e.getStatus()) : null, e.getIteration(),
                e.getRetryCount(), e.getMaxIterations(), e.getPatchApplied(), e.getCompilePassed(), e.getRescanPassed(),
                e.getTestsPassed(), e.getCheckpointJson(), e.getFinalSummary(), e.getErrorCode(), e.getErrorDetail(),
                e.getUpdatedAt());
    }

    private MemoryMessageRecord mapToMemoryMessageRecord(com.cb.auditagent.entity.MemoryMessage e) {
        if (e == null)
            return null;
        return new MemoryMessageRecord(e.getMessageId(), e.getThreadId(), e.getRunId(), e.getSequenceNumber(),
                e.getRole(), e.getMessageType(), e.getContentRedacted(), e.getMetadataJson(), e.getCreatedAt());
    }

    private RepositoryMemory mapToRepositoryMemory(com.cb.auditagent.entity.RepositoryMemory e) {
        if (e == null)
            return null;
        return new RepositoryMemory(e.getMemoryId(), e.getRepoPath(), e.getFindingFingerprint(), e.getRuleId(),
                e.getCategory(), e.getVulnerabilityType(), e.getLanguage(), e.getFramework(), e.getFilePattern(),
                e.getTitle(), e.getSummary(), e.getRootCause(), e.getRemediationPattern(), e.getSearchText(),
                e.getConfidence(), e.getSourceRunId(), e.getUsageCount(), e.getUpdatedAt(), 0);
    }

    private RunChangeRecord mapToRunChangeRecord(com.cb.auditagent.entity.RunChange e) {
        if (e == null)
            return null;
        return new RunChangeRecord(e.getChangeId(), e.getRunId(), e.getFilePath(), e.getBeforeHash(), e.getAfterHash(),
                e.getBackupPath(), e.getState());
    }

    private GitHubAuthorization mapToGitHubAuthorization(com.cb.auditagent.entity.GithubAuthorization e) {
        if (e == null)
            return null;
        return new GitHubAuthorization(e.getUserId(), e.getAccessTokenEncrypted(), e.getAccessExpiresAt(),
                e.getRefreshTokenEncrypted(), e.getRefreshExpiresAt());
    }

    @SuppressWarnings("unused")
    private AuthenticatedUser mapToAuthenticatedUser(com.cb.auditagent.entity.AppUser e) {
        if (e == null)
            return null;
        return new AuthenticatedUser(e.getUserId(), e.getGithubUserId(), e.getLogin(), e.getDisplayName(),
                e.getAvatarUrl(), null);
    }

    private ManagedRepository mapToManagedRepository(com.cb.auditagent.entity.ManagedRepository e) {
        if (e == null)
            return null;
        return new ManagedRepository(e.getRepositoryId(), e.getInstallationId(), null, null, e.getFullName(),
                e.getCloneUrl(), e.getDefaultBranch(), false, e.getPermission());
    }

    private ScanSnapshot mapToScanSnapshot(com.cb.auditagent.entity.ScanSnapshot e) {
        if (e == null)
            return null;
        return new ScanSnapshot(e.getSnapshotId(), e.getRepositoryId(), e.getBranch(), e.getBaseSha(), e.getReportKey(),
                e.getWorkspacePath(), e.getCreatedByUserId(), e.getCreatedAt());
    }

    private RunPublicationRecord mapToRunPublicationRecord(com.cb.auditagent.entity.RunPublication e) {
        if (e == null)
            return null;
        return new RunPublicationRecord(e.getRunId(), e.getUserId(), e.getRepositoryId(), e.getInstallationId(),
                e.getReportKey(), e.getBaseBranch(), e.getBaseSha(), e.getWorkspacePath(), e.getBranchName(),
                e.getApprovalDigest(), e.getApprovedBy(), e.getApprovedAt(), e.getCommitSha(), e.getPushStatus(),
                e.getPrNumber(), e.getPrUrl(), e.getPrState(), e.getPublishError());
    }

    private ScanHistoryRecord mapToScanHistoryRecord(com.cb.auditagent.entity.ScanHistoryStats e) {
        if (e == null)
            return null;
        return new ScanHistoryRecord(e.getHistoryId(), e.getRepositoryId(), e.getBranch(), e.getBaseSha(),
                e.getCreatedAt(), e.getTotalFindings(), e.getHighSeverity(), e.getMediumSeverity(), e.getLowSeverity(),
                e.getInfoSeverity());
    }

    private Notification mapToNotification(com.cb.auditagent.entity.UserNotification e) {
        if (e == null)
            return null;
        Notification d = new Notification();
        d.setId(e.getId());
        d.setUserId(e.getUserId());
        d.setType(e.getType());
        d.setMessage(e.getMessage());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toInstant(java.time.ZoneOffset.UTC) : null);
        return d;
    }

    public record GlobalPattern(String ruleId, String positivePattern, String negativePattern) {
    }

    public Map<String, String> getVerificationEvidence(String runId) {
        Map<String, String> map = new HashMap<>();
        verificationResultRepository.findByRunIdOrderByCreatedAtAsc(runId).forEach(v -> {
            map.put(v.getKind(), v.getEvidenceRedacted());
        });
        return map;
    }

    public List<Map<String, Object>> getTokenUsageForUser(String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        tokenUsageRepository.findTokenUsageSummaryByUserId(userId).forEach(summary -> {
            Map<String, Object> map = new HashMap<>();
            map.put("repoPath", summary.getRepoPath());
            map.put("modelName", summary.getModelName());
            map.put("totalTokens", summary.getTotalTokens());
            map.put("callCount", summary.getCallCount());
            result.add(map);
        });
        return result;
    }

    public void init() {
    }

    @Transactional
    public void withTransaction(java.util.function.Consumer<Connection> action) {
        action.accept(null);
    }

    public String normalizePath(String path) {
        return Path.of(path).normalize().toString();
    }

    @Transactional
    public void saveReport(String repoPath, String mdReport, String htmlReport, List<Vulnerability> findings,
            ScanMetadata metadata) {

        for (Vulnerability v : findings) {
            String snippet = v.getCodeSnippet() == null ? "" : v.getCodeSnippet();
            String fp = sha256(v.getRuleId() + v.getFilePath() + snippet);

            Optional<com.cb.auditagent.entity.VulnerabilityEntity> existingOpt = vulnerabilityEntityRepository
                    .findFirstByRepoPathAndFindingFingerprint(repoPath, fp);

            com.cb.auditagent.entity.VulnerabilityEntity vulnEntity = new com.cb.auditagent.entity.VulnerabilityEntity();
            if (existingOpt.isPresent()) {
                com.cb.auditagent.entity.VulnerabilityEntity existing = existingOpt.get();
                vulnEntity.setId(existing.getId());
                v.setId(existing.getId()); // Synchronize domain object ID
                vulnEntity.setStatus(existing.getStatus());
                v.setStatus(VulnerabilityStatus.valueOf(existing.getStatus())); // Sync status
                vulnEntity.setProposedFix(existing.getProposedFix());
                v.setProposedFix(existing.getProposedFix()); // Sync fix
            } else {
                if (v.getId() == null)
                    v.setId(UUID.randomUUID().toString());
                vulnEntity.setId(v.getId());
                vulnEntity.setStatus("DETECTED");
                v.setStatus(VulnerabilityStatus.DETECTED);
            }
            vulnEntity.setRepoPath(repoPath);
            vulnEntity.setFilePath(v.getFilePath());
            vulnEntity.setLineNumber(v.getLineNumber());
            vulnEntity.setCodeSnippet(v.getCodeSnippet());
            vulnEntity.setSeverity(v.getSeverity().name());
            vulnEntity.setVulnType(v.getVulnType());
            vulnEntity.setDescription(v.getDescription());
            vulnEntity.setRuleId(v.getRuleId());
            vulnEntity.setFindingFingerprint(fp);
            vulnEntity.setLanguage(v.getLanguage());
            vulnerabilityEntityRepository.save(vulnEntity);
        }

        com.cb.auditagent.entity.Report entity = new com.cb.auditagent.entity.Report();
        entity.setRepoPath(repoPath);
        entity.setMdReport(mdReport);
        entity.setHtmlReport(htmlReport);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            entity.setFindingsJson(mapper.writeValueAsString(findings));
            entity.setMetadataJson(mapper.writeValueAsString(metadata));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize report metadata/findings", e);
        }
        reportRepository.save(entity);
    }

    @Transactional
    public void saveScanHistory(ScanHistoryRecord record) {
        com.cb.auditagent.entity.ScanHistoryStats stats = new com.cb.auditagent.entity.ScanHistoryStats();
        stats.setHistoryId(record.historyId());
        stats.setRepositoryId(record.repositoryId());
        stats.setBranch(record.branch());
        stats.setBaseSha(record.baseSha());
        stats.setCreatedAt(record.createdAt());
        stats.setTotalFindings(record.totalFindings());
        stats.setHighSeverity(record.highSeverity());
        stats.setMediumSeverity(record.mediumSeverity());
        stats.setLowSeverity(record.lowSeverity());
        stats.setInfoSeverity(record.infoSeverity());
        scanHistoryStatsRepository.save(stats);
    }

    public List<ScanHistoryRecord> getScanHistory(long repositoryId, String branch) {
        return scanHistoryStatsRepository.findTop50ByRepositoryIdAndBranchOrderByCreatedAtDesc(repositoryId, branch)
                .stream().map(this::mapToScanHistoryRecord).collect(Collectors.toList());
    }

    @Transactional
    public void deleteScanHistory(long repositoryId, String branch) {
        String repoPath = "github:" + repositoryId + ":" + branch;
        scanHistoryStatsRepository.deleteByRepositoryIdAndBranch(repositoryId, branch);
        scanSnapshotRepository.deleteByRepositoryIdAndBranch(repositoryId, branch);
        reportRepository.deleteById(repoPath);
        vulnerabilityEntityRepository.deleteByRepoPath(repoPath);
        agentRunRepository.deleteByRepoPath(repoPath);
    }

    public Optional<Report> getReport(String repoPath) {
        return reportRepository.findById(repoPath).map(this::mapToReport);
    }

    public Optional<Vulnerability> getVulnerabilityById(String vulnId) {
        return vulnerabilityEntityRepository.findById(vulnId).map(this::mapToVulnerability);
    }

    public Optional<Vulnerability> getVulnerabilityById(String vulnId, String repoPath) {
        return vulnerabilityEntityRepository.findById(vulnId).map(this::mapToVulnerability);
    }

    @Transactional
    public void updateVulnerabilityStatus(String vulnId, VulnerabilityStatus status, String proposedFix) {
        vulnerabilityEntityRepository.findById(vulnId).ifPresent(v -> {
            v.setStatus(status.name());
            v.setProposedFix(proposedFix);
            vulnerabilityEntityRepository.save(v);
        });
    }

    @Transactional
    public void updateVulnerabilityStatusTx(Connection conn, String vulnId, VulnerabilityStatus status,
            String proposedFix) throws java.sql.SQLException {
        updateVulnerabilityStatus(vulnId, status, proposedFix);
    }

    @Transactional
    public void unused_updateVulnerabilityStatusTx(Connection conn, String vulnId, VulnerabilityStatus status,
            String proposedFix) {
        updateVulnerabilityStatus(vulnId, status, proposedFix);
    }

    public String getOrCreateMemoryThread(String clientId, String repoPath) {
        Optional<com.cb.auditagent.entity.MemoryThread> threadOpt = memoryThreadRepository.findByClientIdAndRepoPath(
                clientId,
                repoPath);
        if (threadOpt.isPresent()) {
            return threadOpt.get().getThreadId();
        }
        com.cb.auditagent.entity.MemoryThread newThread = new com.cb.auditagent.entity.MemoryThread();
        newThread.setThreadId(UUID.randomUUID().toString());
        newThread.setClientId(clientId);
        newThread.setRepoPath(repoPath);
        newThread.setState("ACTIVE");
        newThread.setCreatedAt(LocalDateTime.now());
        newThread.setUpdatedAt(LocalDateTime.now());
        newThread.setLastAccessedAt(LocalDateTime.now());
        memoryThreadRepository.save(newThread);
        return newThread.getThreadId();
    }

    public boolean memoryThreadExists(String threadId, String repoPath) {
        return memoryThreadRepository.existsByThreadIdAndRepoPath(threadId, repoPath);
    }

    @Transactional
    public void ensureMemoryThread(String threadId, String clientId, String repoPath) {
        if (memoryThreadRepository.findById(threadId).isEmpty()) {
            com.cb.auditagent.entity.MemoryThread t = new com.cb.auditagent.entity.MemoryThread();
            t.setThreadId(threadId);
            t.setClientId(clientId);
            t.setRepoPath(repoPath);
            t.setState("ACTIVE");
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            t.setLastAccessedAt(LocalDateTime.now());
            memoryThreadRepository.save(t);
        }
    }

    @Transactional
    public MemoryMessageRecord appendMemoryMessage(String threadId, String runId, String role, String messageType,
            String content, String metadataJson) {
        Integer currentSeq = memoryMessageRepository.findMaxSequenceNumberByThreadId(threadId);
        long seq = (currentSeq == null ? 0 : currentSeq) + 1L;
        com.cb.auditagent.entity.MemoryMessage msg = new com.cb.auditagent.entity.MemoryMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setThreadId(threadId);
        msg.setRunId(runId);
        msg.setSequenceNumber(seq);
        msg.setRole(role);
        msg.setMessageType(messageType);
        msg.setContentRedacted(content);
        msg.setContentHash(sha256(content));
        msg.setTokenEstimate(estimateTokens(content));
        msg.setMetadataJson(metadataJson);
        msg.setCreatedAt(LocalDateTime.now());
        if (memoryConfig != null && memoryConfig.getRetentionDays() > 0) {
            msg.setExpiresAt(LocalDateTime.now().plusDays(memoryConfig.getRetentionDays()));
        }
        memoryMessageRepository.save(msg);

        memoryThreadRepository.findById(threadId).ifPresent(t -> {
            t.setLastAccessedAt(LocalDateTime.now());
            memoryThreadRepository.save(t);
        });

        return mapToMemoryMessageRecord(msg);
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId) {
        return memoryMessageRepository.findActiveByThreadId(threadId, LocalDateTime.now())
                .stream().map(this::mapToMemoryMessageRecord).collect(Collectors.toList());
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId, String runId, boolean includeGeneralChat) {
        return memoryMessageRepository.findFilteredMessages(threadId, runId, includeGeneralChat, LocalDateTime.now())
                .stream().map(this::mapToMemoryMessageRecord).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMemoryThread(String threadId) {
        memoryMessageRepository.deleteByThreadId(threadId);
        agentRunRepository.deleteByThreadId(threadId);
        graphCheckpointRepository.deleteByThreadId(threadId);
        memoryThreadRepository.deleteById(threadId);
    }

    @Transactional
    public AgentRunRecord createAgentRun(String threadId, Vulnerability vulnerability, String repoPath,
            int maxIterations) {
        if (!agentRunRepository.findActiveRunsByThreadOrRepo(threadId, repoPath, List.of("ACTIVE", "INTERRUPTED"))
                .isEmpty()) {
            throw new IllegalStateException("An agent run is already active for this repository or thread");
        }
        com.cb.auditagent.entity.AgentRun run = new com.cb.auditagent.entity.AgentRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setThreadId(threadId);
        run.setVulnerabilityId(vulnerability != null ? vulnerability.getId() : null);
        run.setFindingFingerprint(vulnerability != null ? vulnerability.getFindingFingerprint() : null);
        run.setRepoPath(repoPath);
        run.setIteration(0);
        run.setRetryCount(0);
        run.setMaxIterations(maxIterations);
        run.setPatchApplied(false);
        run.setCompilePassed(false);
        run.setRescanPassed(false);
        run.setTestsPassed(false);
        run.setPhase(AgentPhase.PLANNING.name());
        run.setStatus(AgentRunStatus.ACTIVE.name());
        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        agentRunRepository.save(run);
        return mapToAgentRunRecord(run);
    }

    @Transactional
    public AgentRunRecord createManagedAgentRun(String threadId, Vulnerability vulnerability, String workspacePath,
            int maxIterations) {
        if (!agentRunRepository.findActiveRunsByThreadOrRepo(threadId, workspacePath, List.of("ACTIVE", "INTERRUPTED"))
                .isEmpty()) {
            throw new IllegalStateException("An agent run is already active for this repository or thread");
        }
        com.cb.auditagent.entity.AgentRun run = new com.cb.auditagent.entity.AgentRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setThreadId(threadId);
        run.setVulnerabilityId(vulnerability != null ? vulnerability.getId() : null);
        run.setFindingFingerprint(vulnerability != null ? vulnerability.getFindingFingerprint() : null);
        run.setRepoPath(workspacePath);
        run.setIteration(0);
        run.setRetryCount(0);
        run.setMaxIterations(maxIterations);
        run.setPatchApplied(false);
        run.setCompilePassed(false);
        run.setRescanPassed(false);
        run.setTestsPassed(false);
        run.setPhase(AgentPhase.PLANNING.name());
        run.setStatus(AgentRunStatus.ACTIVE.name());
        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        agentRunRepository.save(run);
        return mapToAgentRunRecord(run);
    }

    @Transactional
    public void updateAgentRun(String runId, AgentPhase phase, AgentRunStatus status, int iteration, int retryCount,
            boolean patchApplied, boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail) {
        agentRunRepository.findById(runId).ifPresent(r -> {
            r.setPhase(phase != null ? phase.name() : null);
            r.setStatus(status != null ? status.name() : null);
            r.setIteration(iteration);
            r.setRetryCount(retryCount);
            r.setPatchApplied(patchApplied);
            r.setCompilePassed(compilePassed);
            r.setRescanPassed(rescanPassed);
            r.setTestsPassed(testsPassed);
            r.setCheckpointJson(checkpointJson);
            r.setFinalSummary(finalSummary);
            r.setErrorCode(errorCode);
            r.setErrorDetail(errorDetail);
            r.setUpdatedAt(LocalDateTime.now());
            if (status == AgentRunStatus.APPROVED || status == AgentRunStatus.FAILED
                    || status == AgentRunStatus.REJECTED || status == AgentRunStatus.DISCARDED
                    || status == AgentRunStatus.PR_MERGED || status == AgentRunStatus.PR_CLOSED) {
                r.setCompletedAt(LocalDateTime.now());
            }
            agentRunRepository.save(r);
        });
    }

    @Transactional
    public void updateAgentRunTx(Connection conn, String runId, AgentPhase phase, AgentRunStatus status, int iteration,
            int retryCount, boolean patchApplied, boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail)
            throws java.sql.SQLException {
        updateAgentRun(runId, phase, status, iteration, retryCount, patchApplied, compilePassed, rescanPassed,
                testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
    }

    @Transactional
    public void unused_updateAgentRunTx(Connection conn, String runId, AgentPhase phase, AgentRunStatus status,
            int iteration, int retryCount, boolean patchApplied, boolean compilePassed, boolean rescanPassed,
            boolean testsPassed, String checkpointJson, String finalSummary, String errorCode, String errorDetail) {
        updateAgentRun(runId, phase, status, iteration, retryCount, patchApplied, compilePassed, rescanPassed,
                testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
    }

    public Optional<AgentRunRecord> getAgentRun(String runId) {
        return agentRunRepository.findById(runId).map(this::mapToAgentRunRecord);
    }

    public Optional<AgentRunRecord> findRecoverableRun(String threadId) {
        return agentRunRepository
                .findFirstByThreadIdAndStatusInOrderByUpdatedAtDesc(threadId, List.of(
                        "ACTIVE", "INTERRUPTED", "AWAITING_APPROVAL", "PUBLISHING", "PUBLISH_FAILED", "PR_OPEN"))
                .map(this::mapToAgentRunRecord);
    }

    public List<AgentRunRecord> findRecoverableRuns(String threadId) {
        return agentRunRepository
                .findByThreadIdAndStatusInOrderByUpdatedAtDesc(threadId, List.of(
                        "ACTIVE", "INTERRUPTED", "AWAITING_APPROVAL", "PUBLISHING", "PUBLISH_FAILED", "PR_OPEN"))
                .stream().map(this::mapToAgentRunRecord).toList();
    }

    @Transactional
    public boolean appendAgentStep(String runId, int iteration, String action, String toolName, String arguments,
            String result, String resultStatus, long durationMs, String idempotencyKey) {
        if (idempotencyKey != null && agentStepRepository.existsByRunIdAndIdempotencyKey(runId, idempotencyKey)) {
            return false;
        }
        Integer currentSeq = agentStepRepository.findMaxSequenceNumberByRunId(runId);
        long seq = (currentSeq == null ? 0 : currentSeq) + 1L;
        com.cb.auditagent.entity.AgentStep step = new com.cb.auditagent.entity.AgentStep();
        step.setStepId(UUID.randomUUID().toString());
        step.setRunId(runId);
        step.setIteration(iteration);
        step.setSequenceNumber(seq);
        step.setAction(action);
        step.setToolName(toolName);

        int maxChars = memoryConfig != null ? memoryConfig.getMaxToolChars() : 4000;
        step.setArgumentsRedacted(truncate(arguments, maxChars));
        step.setResultRedacted(truncate(result, maxChars));

        step.setResultStatus(resultStatus);
        step.setDurationMs(durationMs);
        step.setIdempotencyKey(idempotencyKey);
        step.setCreatedAt(LocalDateTime.now());
        step.setExpiresAt(LocalDateTime.now().plusDays(30));
        agentStepRepository.save(step);
        return true;
    }

    public boolean hasCompletedStep(String runId, String idempotencyKey) {
        return agentStepRepository.existsByRunIdAndIdempotencyKeyAndResultStatus(runId, idempotencyKey, "SUCCESS");
    }

    @Transactional
    public void addVerificationResult(String runId, String kind, String status, String evidence, Integer exitCode) {
        com.cb.auditagent.entity.VerificationResult res = new com.cb.auditagent.entity.VerificationResult();
        res.setVerificationId(UUID.randomUUID().toString());
        res.setRunId(runId);
        res.setKind(kind);
        res.setStatus(status);
        res.setEvidenceRedacted(evidence);
        res.setExitCode(exitCode);
        res.setCreatedAt(LocalDateTime.now());
        verificationResultRepository.save(res);
    }

    @Transactional
    public void addRunChange(String runId, String filePath, String beforeHash, String afterHash, String backupPath) {
        com.cb.auditagent.entity.RunChange c = new com.cb.auditagent.entity.RunChange();
        c.setChangeId(UUID.randomUUID().toString());
        c.setRunId(runId);
        c.setFilePath(filePath);
        c.setBeforeHash(beforeHash);
        c.setAfterHash(afterHash);
        c.setBackupPath(backupPath);
        c.setState("APPLIED");
        c.setCreatedAt(LocalDateTime.now());
        runChangeRepository.save(c);
    }

    public List<RunChangeRecord> getRunChanges(String runId) {
        return runChangeRepository.findByRunIdOrderByCreatedAtDesc(runId)
                .stream().map(this::mapToRunChangeRecord).collect(Collectors.toList());
    }

    @Transactional
    public void markRunChangeState(String changeId, String state) {
        runChangeRepository.updateState(changeId, state);
    }

    @Transactional
    public void saveApprovedRepositoryMemory(String repoPath, Vulnerability vulnerability, String runId,
            String summary) {
        String fp = sha256(vulnerability.getRuleId() + vulnerability.getFilePath() + vulnerability.getCodeSnippet());
        Optional<com.cb.auditagent.entity.RepositoryMemory> existingOpt = repositoryMemoryRepository
                .findByRepoPathAndFindingFingerprint(repoPath, fp);
        if (existingOpt.isPresent()) {
            com.cb.auditagent.entity.RepositoryMemory existing = existingOpt.get();
            existing.setConfidence(existing.getConfidence() + 1.0);
            existing.setUsageCount(existing.getUsageCount() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            repositoryMemoryRepository.save(existing);
            duckDbKnowledgeBaseService.syncRepositoryMemory(mapToRepositoryMemory(existing));
        } else {
            com.cb.auditagent.entity.RepositoryMemory mem = new com.cb.auditagent.entity.RepositoryMemory();
            mem.setMemoryId(UUID.randomUUID().toString());
            mem.setRepoPath(repoPath);
            mem.setFindingFingerprint(fp);
            mem.setRuleId(vulnerability.getRuleId());
            mem.setCategory("REMEDIATION");
            mem.setVulnerabilityType(vulnerability.getVulnType());
            mem.setLanguage(vulnerability.getLanguage());
            mem.setSummary(summary);
            mem.setConfidence(1.0);
            mem.setApproved(true);
            mem.setSourceRunId(runId);
            mem.setUsageCount(1);
            mem.setCreatedAt(LocalDateTime.now());
            mem.setUpdatedAt(LocalDateTime.now());
            repositoryMemoryRepository.save(mem);
            duckDbKnowledgeBaseService.syncRepositoryMemory(mapToRepositoryMemory(mem));
        }
    }

    @Transactional
    public void saveApprovedRepositoryMemoryTx(Connection conn, String repoPath, Vulnerability vulnerability,
            String runId, String summary) throws java.sql.SQLException {
        saveApprovedRepositoryMemory(repoPath, vulnerability, runId, summary);
    }

    @Transactional
    public void unused_saveApprovedRepositoryMemoryTx(Connection conn, String repoPath, Vulnerability vulnerability,
            String runId, String summary) {
        saveApprovedRepositoryMemory(repoPath, vulnerability, runId, summary);
    }

    @Transactional
    public void saveRejectedRepositoryMemory(String runId, String reason) {
        agentRunRepository.findById(runId).ifPresent(run -> {
            com.cb.auditagent.entity.RepositoryMemory mem = new com.cb.auditagent.entity.RepositoryMemory();
            mem.setMemoryId(UUID.randomUUID().toString());
            mem.setRepoPath(run.getRepoPath());
            mem.setFindingFingerprint(run.getFindingFingerprint());
            mem.setApproved(false);
            mem.setConfidence(0.0);
            mem.setSummary(reason);
            mem.setSourceRunId(runId);
            mem.setUsageCount(1);
            mem.setCreatedAt(LocalDateTime.now());
            mem.setUpdatedAt(LocalDateTime.now());
            repositoryMemoryRepository.save(mem);
            duckDbKnowledgeBaseService.syncRepositoryMemory(mapToRepositoryMemory(mem));
        });
    }

    @Transactional
    public void lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) throws java.sql.SQLException {
        repositoryMemoryRepository.lowerConfidence(runId, vulnerability.getFindingFingerprint(), LocalDateTime.now());
    }

    @Transactional
    public void unused_lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) {
        repositoryMemoryRepository.lowerConfidence(runId, vulnerability.getFindingFingerprint(), LocalDateTime.now());
    }

    @Transactional
    public void saveGlobalPatternTx(Connection conn, String ruleId, String positivePattern, String negativePattern,
            String runId) throws java.sql.SQLException {
        Optional<com.cb.auditagent.entity.GlobalRemediationPattern> existingOpt = globalRemediationPatternRepository
                .findByRuleId(ruleId);
        if (existingOpt.isPresent()) {
            com.cb.auditagent.entity.GlobalRemediationPattern existing = existingOpt.get();
            existing.setPositivePattern(positivePattern);
            existing.setNegativePattern(negativePattern);
            existing.setUpdatedAt(LocalDateTime.now());
            globalRemediationPatternRepository.save(existing);
        } else {
            com.cb.auditagent.entity.GlobalRemediationPattern p = new com.cb.auditagent.entity.GlobalRemediationPattern();
            p.setPatternId(UUID.randomUUID().toString());
            p.setRuleId(ruleId);
            p.setPositivePattern(positivePattern);
            p.setNegativePattern(negativePattern);
            p.setCreatedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());
            globalRemediationPatternRepository.save(p);
        }
    }

    @Transactional
    public void unused_saveGlobalPatternTx(Connection conn, String ruleId, String positivePattern,
            String negativePattern, String runId) {
        try {
            saveGlobalPatternTx(conn, ruleId, positivePattern, negativePattern, runId);
        } catch (Exception e) {
        }
    }

    public List<RepositoryMemory> searchRepositoryMemories(String repoPath, Vulnerability vulnerability) {
        return duckDbKnowledgeBaseService.searchRepositoryMemories(repoPath, vulnerability);
    }

    public List<RepositoryMemory> searchRejectedMemories(String repoPath, Vulnerability vulnerability) {
        return duckDbKnowledgeBaseService.searchRejectedMemories(repoPath, vulnerability);
    }

    @Transactional
    public void deleteRepositoryMemories(String repoPath) {
        repositoryMemoryRepository.deleteByRepoPath(repoPath);
    }

    @Transactional
    public void ensureFtsIndex() {
        duckDbKnowledgeBaseService.ensureFtsIndex();
    }

    public boolean isFtsAvailable() {
        return duckDbKnowledgeBaseService.isFtsAvailable();
    }

    @Transactional
    public String upsertGitHubUser(long githubUserId, String login, String displayName, String avatarUrl) {
        Optional<com.cb.auditagent.entity.AppUser> existingOpt = appUserRepository.findByGithubUserId(githubUserId);
        if (existingOpt.isPresent()) {
            com.cb.auditagent.entity.AppUser existing = existingOpt.get();
            existing.setLogin(login);
            existing.setDisplayName(displayName);
            existing.setAvatarUrl(avatarUrl);
            existing.setUpdatedAt(LocalDateTime.now());
            appUserRepository.save(existing);
            return existing.getUserId();
        } else {
            com.cb.auditagent.entity.AppUser u = new com.cb.auditagent.entity.AppUser();
            String newId = UUID.randomUUID().toString();
            u.setUserId(newId);
            u.setGithubUserId(githubUserId);
            u.setLogin(login);
            u.setDisplayName(displayName);
            u.setAvatarUrl(avatarUrl);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            appUserRepository.save(u);
            return newId;
        }
    }

    @Transactional
    public void saveGitHubAuthorization(GitHubAuthorization authorization) {
        com.cb.auditagent.entity.GithubAuthorization auth = githubAuthorizationRepository
                .findById(authorization.userId()).orElse(new com.cb.auditagent.entity.GithubAuthorization());
        auth.setUserId(authorization.userId());
        auth.setAccessTokenEncrypted(authorization.accessTokenEncrypted());
        auth.setAccessExpiresAt(authorization.accessExpiresAt());
        auth.setRefreshTokenEncrypted(authorization.refreshTokenEncrypted());
        auth.setRefreshExpiresAt(authorization.refreshExpiresAt());
        auth.setUpdatedAt(LocalDateTime.now());
        githubAuthorizationRepository.save(auth);
    }

    public Optional<GitHubAuthorization> getGitHubAuthorization(String userId) {
        return githubAuthorizationRepository.findById(userId).map(this::mapToGitHubAuthorization);
    }

    @Transactional
    public void saveOAuthState(String stateHash, String verifierEncrypted, LocalDateTime expiresAt) {
        com.cb.auditagent.entity.OauthState s = new com.cb.auditagent.entity.OauthState();
        s.setStateHash(stateHash);
        s.setVerifierEncrypted(verifierEncrypted);
        s.setExpiresAt(expiresAt);
        s.setCreatedAt(LocalDateTime.now());
        oauthStateRepository.save(s);
    }

    public Optional<String> consumeOAuthState(String stateHash) {
        Optional<com.cb.auditagent.entity.OauthState> s = oauthStateRepository
                .findByStateHashAndExpiresAtAfter(stateHash, LocalDateTime.now(java.time.ZoneOffset.UTC));
        if (s.isPresent()) {
            oauthStateRepository.delete(s.get());
            return Optional.ofNullable(s.get().getVerifierEncrypted());
        }
        return Optional.empty();
    }

    @Transactional
    public void createUserSession(String sessionHash, String userId, String csrfToken, LocalDateTime expiresAt) {
        com.cb.auditagent.entity.UserSession s = new com.cb.auditagent.entity.UserSession();
        s.setSessionHash(sessionHash);
        s.setUserId(userId);
        s.setCsrfToken(csrfToken);
        s.setExpiresAt(expiresAt);
        s.setLastSeenAt(LocalDateTime.now());
        s.setCreatedAt(LocalDateTime.now());
        userSessionRepository.save(s);
    }

    public Optional<AuthenticatedUser> findAuthenticatedUser(String sessionHash) {
        return userSessionRepository
                .findBySessionHashAndExpiresAtAfter(sessionHash, LocalDateTime.now(java.time.ZoneOffset.UTC))
                .flatMap(s -> appUserRepository.findById(s.getUserId()).map(u -> new AuthenticatedUser(
                        u.getUserId(), u.getGithubUserId(), u.getLogin(), u.getDisplayName(),
                        u.getAvatarUrl(), s.getCsrfToken())));
    }

    @Transactional
    public void deleteUserSession(String sessionHash) {
        userSessionRepository.deleteById(sessionHash);
    }

    @Transactional
    public void upsertManagedRepository(ManagedRepository repository) {
        com.cb.auditagent.entity.ManagedRepository m = managedRepositoryRepository.findById(repository.repositoryId())
                .orElse(new com.cb.auditagent.entity.ManagedRepository());
        m.setRepositoryId(repository.repositoryId());
        m.setInstallationId(repository.installationId());
        m.setOwnerLogin(repository.owner());
        m.setRepoName(repository.name());
        m.setFullName(repository.fullName());
        m.setCloneUrl(repository.cloneUrl());
        m.setDefaultBranch(repository.defaultBranch());
        m.setIsPrivate(repository.privateRepository());
        m.setPermission(repository.permission());
        m.setUpdatedAt(LocalDateTime.now());
        managedRepositoryRepository.save(m);
    }

    public Optional<ManagedRepository> getManagedRepository(long repositoryId) {
        return managedRepositoryRepository.findById(repositoryId).map(this::mapToManagedRepository);
    }

    @Transactional
    public void saveScanSnapshot(ScanSnapshot snapshot) {
        com.cb.auditagent.entity.ScanSnapshot s = new com.cb.auditagent.entity.ScanSnapshot();
        s.setSnapshotId(snapshot.snapshotId());
        s.setRepositoryId(snapshot.repositoryId());
        s.setBranch(snapshot.branch());
        s.setBaseSha(snapshot.baseSha());
        s.setReportKey(snapshot.reportKey());
        s.setWorkspacePath(snapshot.workspacePath());
        s.setCreatedByUserId(snapshot.createdByUserId());
        s.setCreatedAt(snapshot.createdAt());
        scanSnapshotRepository.save(s);
    }

    public Optional<ScanSnapshot> findLatestScanSnapshot(long repositoryId, String branch) {
        return scanSnapshotRepository.findFirstByRepositoryIdAndBranchOrderByCreatedAtDesc(repositoryId, branch)
                .map(this::mapToScanSnapshot);
    }

    @Transactional
    public void createRunPublication(RunPublicationRecord publication) {
        com.cb.auditagent.entity.RunPublication p = new com.cb.auditagent.entity.RunPublication();
        p.setRunId(publication.runId());
        p.setUserId(publication.userId());
        p.setRepositoryId(publication.repositoryId());
        p.setInstallationId(publication.installationId());
        p.setReportKey(publication.reportKey());
        p.setBaseBranch(publication.baseBranch());
        p.setBaseSha(publication.baseSha());
        p.setWorkspacePath(publication.workspacePath());
        p.setBranchName(publication.branchName());
        p.setApprovalDigest(publication.approvalDigest());
        p.setApprovedBy(publication.approvedBy());
        p.setApprovedAt(publication.approvedAt());
        p.setCommitSha(publication.commitSha());
        p.setPushStatus(publication.pushStatus());
        p.setPublishError(publication.publishError());
        p.setUpdatedAt(LocalDateTime.now());
        runPublicationRepository.save(p);
    }

    @Transactional
    public void saveRunPublication(RunPublicationRecord publication) {
        com.cb.auditagent.entity.RunPublication p = runPublicationRepository.findById(publication.runId())
                .orElse(new com.cb.auditagent.entity.RunPublication());
        p.setRunId(publication.runId());
        p.setUserId(publication.userId());
        p.setRepositoryId(publication.repositoryId());
        p.setInstallationId(publication.installationId());
        p.setReportKey(publication.reportKey());
        p.setBaseBranch(publication.baseBranch());
        p.setBaseSha(publication.baseSha());
        p.setWorkspacePath(publication.workspacePath());
        p.setBranchName(publication.branchName());
        p.setApprovalDigest(publication.approvalDigest());
        p.setApprovedBy(publication.approvedBy());
        p.setApprovedAt(publication.approvedAt());
        p.setCommitSha(publication.commitSha());
        p.setPushStatus(publication.pushStatus());
        p.setPrNumber(publication.pullRequestNumber());
        p.setPrUrl(publication.pullRequestUrl());
        p.setPrState(publication.pullRequestState());
        p.setPublishError(publication.publishError());
        p.setUpdatedAt(LocalDateTime.now());
        runPublicationRepository.save(p);
    }

    @Transactional
    public void saveRunPublicationTx(Connection conn, RunPublicationRecord publication) throws java.sql.SQLException {
        saveRunPublication(publication);
    }

    @Transactional
    public void unused_saveRunPublicationTx(Connection conn, RunPublicationRecord publication) {
        saveRunPublication(publication);
    }

    public Optional<RunPublicationRecord> getRunPublication(String runId) {
        return runPublicationRepository.findById(runId).map(this::mapToRunPublicationRecord);
    }

    public boolean hasActiveManagedRun(long repositoryId, String branch, String fingerprint) {
        return !runPublicationRepository.findActiveManagedRunIds(repositoryId, branch, fingerprint,
                List.of("ACTIVE", "INTERRUPTED", "PR_OPEN")).isEmpty();
    }

    public List<String> getActiveManagedRunIds(long repositoryId, String branch, String fingerprint) {
        return runPublicationRepository.findActiveManagedRunIds(repositoryId, branch, fingerprint,
                List.of("ACTIVE", "INTERRUPTED", "PR_OPEN"));
    }

    public boolean memoryThreadBelongsTo(String threadId, String userId) {
        return memoryThreadRepository.existsByThreadIdAndClientId(threadId, userId);
    }

    public Optional<RunPublicationRecord> findRunPublication(long repositoryId, int prNumber) {
        return runPublicationRepository.findByRepositoryIdAndPrNumber(repositoryId, prNumber)
                .map(this::mapToRunPublicationRecord);
    }

    public List<RunPublicationRecord> findStaleWorkspaces(List<String> terminalStatuses,
            java.time.LocalDateTime before) {
        return runPublicationRepository.findStaleWorkspaces(terminalStatuses, before).stream()
                .map(this::mapToRunPublicationRecord).collect(Collectors.toList());
    }

    public boolean recordWebhookDelivery(String deliveryId, String eventType) {
        if (webhookDeliveryRepository.existsById(deliveryId)) {
            return false;
        }
        com.cb.auditagent.entity.WebhookDelivery w = new com.cb.auditagent.entity.WebhookDelivery();
        w.setDeliveryId(deliveryId);
        w.setEventType(eventType);
        w.setReceivedAt(LocalDateTime.now());
        webhookDeliveryRepository.save(w);
        return true;
    }

    @Transactional
    public void releaseWebhookDelivery(String deliveryId) {
        webhookDeliveryRepository.deleteById(deliveryId);
    }

    @Transactional
    public void deleteExpiredMemory() {
        LocalDateTime now = LocalDateTime.now();
        oauthStateRepository.deleteExpiredStates(now);
        userSessionRepository.deleteExpiredSessions(now);
        agentStepRepository.deleteExpiredSteps(now);
        memoryMessageRepository.deleteExpiredMessages(now);
        graphCheckpointRepository.nullifyExpiredStateJson(now);
        graphNodeEventRepository.nullifyExpiredDetailJson(now);
        subagentStateRepository.nullifyExpiredData(now);
        LocalDateTime cutoff30days = now.minusDays(30);
        agentRunRepository.clearExpiredErrorDetails(cutoff30days);
        verificationResultRepository.nullifyExpiredEvidence(cutoff30days);
    }

    @Transactional
    public void saveGraphCheckpoint(String checkpointId, String runId, String threadId, String parentId,
            String nodeName, String stateJson, String interruptReason) {
        com.cb.auditagent.entity.GraphCheckpoint c = new com.cb.auditagent.entity.GraphCheckpoint();
        c.setCheckpointId(checkpointId);
        c.setRunId(runId);
        c.setThreadId(threadId);
        c.setParentCheckpointId(parentId);
        c.setNodeName(nodeName);
        c.setStateJson(stateJson);
        c.setInterruptReason(interruptReason);
        c.setCreatedAt(LocalDateTime.now());
        c.setExpiresAt(LocalDateTime.now().plusDays(30));
        graphCheckpointRepository.save(c);
    }

    public String[] loadGraphCheckpoint(String checkpointId) {
        return graphCheckpointRepository.findValidCheckpoint(checkpointId, LocalDateTime.now())
                .map(c -> new String[] { c.getStateJson(), c.getNodeName() })
                .orElse(new String[0]);
    }

    @Transactional
    public void deleteGraphCheckpoints(String workflowId) {
        graphCheckpointRepository.deleteByThreadId(workflowId);
    }

    public String loadLatestGraphCheckpoint(String runId) {
        return graphCheckpointRepository.findFirstByRunIdAndStateJsonIsNotNullOrderByCreatedAtDesc(runId)
                .map(com.cb.auditagent.entity.GraphCheckpoint::getCheckpointId)
                .orElse("");
    }

    public List<String> listGraphCheckpoints(String threadId) {
        return graphCheckpointRepository.findCheckpointIdsByThreadIdOrderByCreatedAtAsc(threadId);
    }

    @Transactional
    public void updateAgentRunCheckpointJson(String runId, String json) {
        agentRunRepository.updateCheckpointJson(runId, json, LocalDateTime.now());
    }

    @Transactional
    public void saveNodeEvent(String eventId, String runId, String checkpointId, String nodeName, String status,
            Long durationMs, String detailJson) {
        com.cb.auditagent.entity.GraphNodeEvent ev = new com.cb.auditagent.entity.GraphNodeEvent();
        ev.setEventId(eventId);
        ev.setRunId(runId);
        ev.setCheckpointId(checkpointId);
        ev.setNodeName(nodeName);
        ev.setStatus(status);
        ev.setDurationMs(durationMs);
        ev.setDetailJson(detailJson);
        ev.setCreatedAt(LocalDateTime.now());
        ev.setExpiresAt(LocalDateTime.now().plusDays(30));
        graphNodeEventRepository.save(ev);
    }

    public List<String> getNodeEvents(String runId) {
        return graphNodeEventRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(com.cb.auditagent.entity.GraphNodeEvent::getDetailJson)
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveSubagentState(String stateId, String runId, String parentCheckpointId, String childAgent,
            int sequenceNumber, String contextJson, String resultJson, String status) {
        com.cb.auditagent.entity.SubagentState s = new com.cb.auditagent.entity.SubagentState();
        s.setStateId(stateId);
        s.setRunId(runId);
        s.setParentCheckpointId(parentCheckpointId);
        s.setChildAgent(childAgent);
        s.setSequenceNumber(sequenceNumber);
        s.setContextJson(contextJson);
        s.setResultJson(resultJson);
        s.setStatus(status);
        s.setCreatedAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(30));
        subagentStateRepository.save(s);
    }

    @Transactional
    public void updateSubagentResult(String stateId, String resultJson, String status) {
        subagentStateRepository.findById(stateId).ifPresent(s -> {
            s.setResultJson(resultJson);
            s.setStatus(status);
            subagentStateRepository.save(s);
        });
    }

    public String getSubagentContext(String runId, String childAgent) {
        return subagentStateRepository
                .findFirstByRunIdAndChildAgentAndStatusOrderBySequenceNumberDesc(runId, childAgent, "PENDING")
                .map(com.cb.auditagent.entity.SubagentState::getContextJson)
                .orElse("");
    }

    @Transactional
    public void saveTokenUsage(String threadId, String runId, String vulnerabilityId, String modelName, int tokens) {
        memoryThreadRepository.findById(threadId).ifPresent(thread -> {
            com.cb.auditagent.entity.TokenUsage t = new com.cb.auditagent.entity.TokenUsage();
            t.setId(UUID.randomUUID().toString());
            t.setUserId(thread.getClientId());
            t.setThreadId(threadId);
            t.setRunId(runId);
            t.setVulnerabilityId(vulnerabilityId);
            t.setRepoPath(thread.getRepoPath());
            t.setModelName(modelName);
            t.setTokens(tokens);
            t.setCreatedAt(LocalDateTime.now());
            tokenUsageRepository.save(t);
        });
    }

    @Transactional
    public void saveNotification(Notification notification) {
        com.cb.auditagent.entity.UserNotification u = new com.cb.auditagent.entity.UserNotification();
        u.setId(notification.getId());
        u.setUserId(notification.getUserId());
        u.setType(notification.getType());
        u.setMessage(notification.getMessage());
        u.setIsRead(false);
        u.setCreatedAt(LocalDateTime.now());
        userNotificationRepository.save(u);
    }

    public List<Notification> getNotifications(String userId) {
        return userNotificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToNotification).collect(Collectors.toList());
    }

    @Transactional
    public void markNotificationsRead(String userId) {
        userNotificationRepository.markAllAsReadByUserId(userId);
    }

    public Optional<DatabaseService.GlobalPattern> getGlobalPattern(String ruleId) {
        return globalRemediationPatternRepository.findByRuleId(ruleId)
                .map(pattern -> new GlobalPattern(pattern.getRuleId(), pattern.getPositivePattern(),
                        pattern.getNegativePattern()));
    }
}
