package com.cb.auditagent.service.data;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain data service for Repository & Scanning aggregates (ManagedRepository, ScanSnapshot, ScanHistoryStats, Report, Vulnerability, WebhookDelivery, TokenUsage).
 */
@Service
public class ScanDataService {

    private static final Logger logger = LoggerFactory.getLogger(ScanDataService.class);

    private final ManagedRepositoryRepository managedRepositoryRepository;
    private final ScanSnapshotRepository scanSnapshotRepository;
    private final ScanHistoryStatsRepository scanHistoryStatsRepository;
    private final ReportRepository reportRepository;
    private final VulnerabilityEntityRepository vulnerabilityEntityRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final MemoryThreadRepository memoryThreadRepository;
    private final TrackedIssueRepository trackedIssueRepository;

    public ScanDataService(ManagedRepositoryRepository managedRepositoryRepository,
                           ScanSnapshotRepository scanSnapshotRepository,
                           ScanHistoryStatsRepository scanHistoryStatsRepository,
                           ReportRepository reportRepository,
                           VulnerabilityEntityRepository vulnerabilityEntityRepository,
                           WebhookDeliveryRepository webhookDeliveryRepository,
                           TokenUsageRepository tokenUsageRepository,
                           MemoryThreadRepository memoryThreadRepository,
                           TrackedIssueRepository trackedIssueRepository) {
        this.managedRepositoryRepository = managedRepositoryRepository;
        this.scanSnapshotRepository = scanSnapshotRepository;
        this.scanHistoryStatsRepository = scanHistoryStatsRepository;
        this.reportRepository = reportRepository;
        this.vulnerabilityEntityRepository = vulnerabilityEntityRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.memoryThreadRepository = memoryThreadRepository;
        this.trackedIssueRepository = trackedIssueRepository;
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
            ObjectMapper mapper = new ObjectMapper();
            if (e.getFindingsJson() != null && !e.getFindingsJson().trim().isEmpty()) {
                d.setFindings(mapper.readValue(e.getFindingsJson(), new TypeReference<List<Vulnerability>>() {}));
            }
            if (e.getMetadataJson() != null && !e.getMetadataJson().trim().isEmpty()) {
                d.setMetadata(mapper.readValue(e.getMetadataJson(), ScanMetadata.class));
            } else {
                d.setMetadata(new ScanMetadata());
            }
        } catch (Exception ex) {
            logger.error("Failed to deserialize report metadata/findings", ex);
            d.setMetadata(new ScanMetadata());
        }
        return d;
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

    private ScanHistoryRecord mapToScanHistoryRecord(com.cb.auditagent.entity.ScanHistoryStats e) {
        if (e == null)
            return null;
        return new ScanHistoryRecord(e.getHistoryId(), e.getRepositoryId(), e.getBranch(), e.getBaseSha(),
                e.getCreatedAt(), e.getTotalFindings(), e.getHighSeverity(), e.getMediumSeverity(), e.getLowSeverity(),
                e.getInfoSeverity());
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
                v.setId(existing.getId());
                vulnEntity.setStatus(existing.getStatus());
                v.setStatus(VulnerabilityStatus.valueOf(existing.getStatus()));
                vulnEntity.setProposedFix(existing.getProposedFix());
                v.setProposedFix(existing.getProposedFix());
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
            ObjectMapper mapper = new ObjectMapper();
            entity.setFindingsJson(mapper.writeValueAsString(findings));
            entity.setMetadataJson(mapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize report metadata/findings", e);
        }
        reportRepository.save(entity);
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

    private TrackedIssueRecord mapToTrackedIssueRecord(com.cb.auditagent.entity.TrackedIssue entity) {
        if (entity == null) return null;
        List<String> labelList = new ArrayList<>();
        if (entity.getLabels() != null && !entity.getLabels().isBlank()) {
            for (String l : entity.getLabels().split(",")) {
                if (!l.isBlank()) labelList.add(l.trim());
            }
        }
        return new TrackedIssueRecord(
                entity.getId(),
                entity.getRepositoryId(),
                entity.getIssueNumber(),
                entity.getTitle(),
                entity.getBody(),
                labelList,
                entity.getState(),
                entity.getTargetVulnerabilityId(),
                entity.getRunId(),
                entity.getRemediationStatus(),
                entity.getPullRequestNumber(),
                entity.getPullRequestUrl(),
                entity.getStatusMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    @Transactional
    public void saveTrackedIssue(TrackedIssueRecord record) {
        if (record == null) return;
        com.cb.auditagent.entity.TrackedIssue entity = trackedIssueRepository.findById(record.id())
                .orElse(new com.cb.auditagent.entity.TrackedIssue());
        entity.setId(record.id());
        entity.setRepositoryId(record.repositoryId());
        entity.setIssueNumber(record.issueNumber());
        entity.setTitle(record.title());
        entity.setBody(record.body());
        entity.setLabels(record.labels() != null ? String.join(",", record.labels()) : null);
        entity.setState(record.state() != null ? record.state() : "OPEN");
        entity.setTargetVulnerabilityId(record.targetVulnerabilityId());
        entity.setRunId(record.runId());
        entity.setRemediationStatus(record.remediationStatus());
        entity.setPullRequestNumber(record.pullRequestNumber());
        entity.setPullRequestUrl(record.pullRequestUrl());
        entity.setStatusMessage(record.statusMessage());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(record.createdAt() != null ? record.createdAt() : LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        trackedIssueRepository.save(entity);
    }

    public List<TrackedIssueRecord> listTrackedIssues(long repositoryId) {
        return trackedIssueRepository.findByRepositoryIdOrderByUpdatedAtDesc(repositoryId).stream()
                .map(this::mapToTrackedIssueRecord)
                .toList();
    }

    public Optional<TrackedIssueRecord> findTrackedIssue(long repositoryId, int issueNumber) {
        return trackedIssueRepository.findByRepositoryIdAndIssueNumber(repositoryId, issueNumber)
                .map(this::mapToTrackedIssueRecord);
    }

    @Transactional
    public void updateTrackedIssueStatusByPr(int pullRequestNumber, String status, String state, String statusMessage) {
        trackedIssueRepository.findByPullRequestNumber(pullRequestNumber).ifPresent(entity -> {
            if (status != null) entity.setRemediationStatus(status);
            if (state != null) entity.setState(state);
            if (statusMessage != null) entity.setStatusMessage(statusMessage);
            entity.setUpdatedAt(LocalDateTime.now());
            trackedIssueRepository.save(entity);
        });
    }
}

