package com.cb.auditagent.service.data;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.entity.AgentRun;
import com.cb.auditagent.entity.AgentStep;
import com.cb.auditagent.entity.GraphCheckpoint;
import com.cb.auditagent.entity.GraphNodeEvent;
import com.cb.auditagent.entity.RunChange;
import com.cb.auditagent.entity.RunPublication;
import com.cb.auditagent.entity.SubagentState;
import com.cb.auditagent.entity.VerificationResult;
import com.cb.auditagent.repository.*;
import com.cb.auditagent.service.DuckDbKnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain data service for Remediation aggregates (AgentRun, AgentStep, VerificationResult,
 * RunChange, GraphCheckpoint, GraphNodeEvent, SubagentState, RunPublication,
 * RepositoryMemory, GlobalRemediationPattern).
 */
@Service
public class RemediationDataService {

    public record GlobalPattern(String ruleId, String positivePattern, String negativePattern) {}

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final VerificationResultRepository verificationResultRepository;
    private final RunChangeRepository runChangeRepository;
    private final GraphCheckpointRepository graphCheckpointRepository;
    private final GraphNodeEventRepository graphNodeEventRepository;
    private final SubagentStateRepository subagentStateRepository;
    private final RunPublicationRepository runPublicationRepository;
    private final RepositoryMemoryRepository repositoryMemoryRepository;
    private final GlobalRemediationPatternRepository globalRemediationPatternRepository;
    private final DuckDbKnowledgeBaseService duckDbKnowledgeBaseService;
    private final MemoryConfig memoryConfig;

    public RemediationDataService(AgentRunRepository agentRunRepository,
                                  AgentStepRepository agentStepRepository,
                                  VerificationResultRepository verificationResultRepository,
                                  RunChangeRepository runChangeRepository,
                                  GraphCheckpointRepository graphCheckpointRepository,
                                  GraphNodeEventRepository graphNodeEventRepository,
                                  SubagentStateRepository subagentStateRepository,
                                  RunPublicationRepository runPublicationRepository,
                                  RepositoryMemoryRepository repositoryMemoryRepository,
                                  GlobalRemediationPatternRepository globalRemediationPatternRepository,
                                  DuckDbKnowledgeBaseService duckDbKnowledgeBaseService,
                                  MemoryConfig memoryConfig) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.verificationResultRepository = verificationResultRepository;
        this.runChangeRepository = runChangeRepository;
        this.graphCheckpointRepository = graphCheckpointRepository;
        this.graphNodeEventRepository = graphNodeEventRepository;
        this.subagentStateRepository = subagentStateRepository;
        this.runPublicationRepository = runPublicationRepository;
        this.repositoryMemoryRepository = repositoryMemoryRepository;
        this.globalRemediationPatternRepository = globalRemediationPatternRepository;
        this.duckDbKnowledgeBaseService = duckDbKnowledgeBaseService;
        this.memoryConfig = memoryConfig;
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

    private String truncate(String value, int max) {
        if (value == null)
            return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private AgentRunRecord mapToAgentRunRecord(AgentRun e) {
        if (e == null)
            return null;
        return new AgentRunRecord(e.getRunId(), e.getThreadId(), e.getVulnerabilityId(), e.getFindingFingerprint(),
                e.getRepoPath(), e.getPhase() != null ? AgentPhase.valueOf(e.getPhase()) : null,
                e.getStatus() != null ? AgentRunStatus.valueOf(e.getStatus()) : null, e.getIteration(),
                e.getRetryCount(), e.getMaxIterations(), e.getPatchApplied(), e.getCompilePassed(), e.getRescanPassed(),
                e.getTestsPassed(), e.getCheckpointJson(), e.getFinalSummary(), e.getErrorCode(), e.getErrorDetail(),
                e.getUpdatedAt());
    }

    private RepositoryMemory mapToRepositoryMemory(com.cb.auditagent.entity.RepositoryMemory e) {
        if (e == null)
            return null;
        return new RepositoryMemory(e.getMemoryId(), e.getRepoPath(), e.getFindingFingerprint(), e.getRuleId(),
                e.getCategory(), e.getVulnerabilityType(), e.getLanguage(), e.getFramework(), e.getFilePattern(),
                e.getTitle(), e.getSummary(), e.getRootCause(), e.getRemediationPattern(), e.getSearchText(),
                e.getConfidence(), e.getSourceRunId(), e.getUsageCount(), e.getUpdatedAt(), 0);
    }

    private RunChangeRecord mapToRunChangeRecord(RunChange e) {
        if (e == null)
            return null;
        return new RunChangeRecord(e.getChangeId(), e.getRunId(), e.getFilePath(), e.getBeforeHash(), e.getAfterHash(),
                e.getBackupPath(), e.getState());
    }

    private RunPublicationRecord mapToRunPublicationRecord(RunPublication e) {
        if (e == null)
            return null;
        return new RunPublicationRecord(e.getRunId(), e.getUserId(), e.getRepositoryId(), e.getInstallationId(),
                e.getReportKey(), e.getBaseBranch(), e.getBaseSha(), e.getWorkspacePath(), e.getBranchName(),
                e.getApprovalDigest(), e.getApprovedBy(), e.getApprovedAt(), e.getCommitSha(), e.getPushStatus(),
                e.getPrNumber(), e.getPrUrl(), e.getPrState(), e.getPublishError());
    }

    public Map<String, String> getVerificationEvidence(String runId) {
        Map<String, String> map = new HashMap<>();
        verificationResultRepository.findByRunIdOrderByCreatedAtAsc(runId).forEach(v -> {
            map.put(v.getKind(), v.getEvidenceRedacted());
        });
        return map;
    }

    @Transactional
    public void addVerificationResult(String runId, String kind, String status, String evidence, Integer exitCode) {
        VerificationResult res = new VerificationResult();
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
    public AgentRunRecord createAgentRun(String threadId, Vulnerability vulnerability, String repoPath,
            int maxIterations) {
        if (!agentRunRepository.findActiveRunsByThreadOrRepo(threadId, repoPath, List.of("ACTIVE", "INTERRUPTED"))
                .isEmpty()) {
            throw new IllegalStateException("An agent run is already active for this repository or thread");
        }
        AgentRun run = new AgentRun();
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
        AgentRun run = new AgentRun();
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
            throws SQLException {
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
        AgentStep step = new AgentStep();
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
    public void addRunChange(String runId, String filePath, String beforeHash, String afterHash, String backupPath) {
        RunChange c = new RunChange();
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
            String runId, String summary) throws SQLException {
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
            String runId) throws SQLException {
        repositoryMemoryRepository.lowerConfidence(runId, vulnerability.getFindingFingerprint(), LocalDateTime.now());
    }

    @Transactional
    public void unused_lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) {
        repositoryMemoryRepository.lowerConfidence(runId, vulnerability.getFindingFingerprint(), LocalDateTime.now());
    }

    @Transactional
    public void saveGlobalPatternTx(Connection conn, String ruleId, String positivePattern, String negativePattern,
            String runId) throws SQLException {
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
    public void createRunPublication(RunPublicationRecord publication) {
        RunPublication p = new RunPublication();
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
        RunPublication p = runPublicationRepository.findById(publication.runId())
                .orElse(new RunPublication());
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
    public void saveRunPublicationTx(Connection conn, RunPublicationRecord publication) throws SQLException {
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

    public Optional<RunPublicationRecord> findRunPublication(long repositoryId, int prNumber) {
        return runPublicationRepository.findByRepositoryIdAndPrNumber(repositoryId, prNumber)
                .map(this::mapToRunPublicationRecord);
    }

    public List<RunPublicationRecord> findStaleWorkspaces(List<String> terminalStatuses, LocalDateTime before) {
        return runPublicationRepository.findStaleWorkspaces(terminalStatuses, before).stream()
                .map(this::mapToRunPublicationRecord).collect(Collectors.toList());
    }

    @Transactional
    public void saveGraphCheckpoint(String checkpointId, String runId, String threadId, String parentId,
            String nodeName, String stateJson, String interruptReason) {
        GraphCheckpoint c = new GraphCheckpoint();
        c.setCheckpointId(checkpointId);
        if (runId != null && agentRunRepository.existsById(runId)) {
            c.setRunId(runId);
        } else {
            c.setRunId(null);
        }
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
                .map(GraphCheckpoint::getCheckpointId)
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
        GraphNodeEvent ev = new GraphNodeEvent();
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
                .map(GraphNodeEvent::getDetailJson)
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveSubagentState(String stateId, String runId, String parentCheckpointId, String childAgent,
            int sequenceNumber, String contextJson, String resultJson, String status) {
        SubagentState s = new SubagentState();
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
                .map(SubagentState::getContextJson)
                .orElse("");
    }

    public Optional<GlobalPattern> getGlobalPattern(String ruleId) {
        return globalRemediationPatternRepository.findByRuleId(ruleId)
                .map(pattern -> new GlobalPattern(pattern.getRuleId(), pattern.getPositivePattern(),
                        pattern.getNegativePattern()));
    }

    @Transactional
    public void deleteRunsAndCheckpointsForThread(String threadId) {
        agentRunRepository.deleteByThreadId(threadId);
        graphCheckpointRepository.deleteByThreadId(threadId);
    }

    @Transactional
    public void deleteExpiredMemory(LocalDateTime now, LocalDateTime cutoff30days) {
        agentStepRepository.deleteExpiredSteps(now);
        graphCheckpointRepository.nullifyExpiredStateJson(now);
        graphNodeEventRepository.nullifyExpiredDetailJson(now);
        subagentStateRepository.nullifyExpiredData(now);
        agentRunRepository.clearExpiredErrorDetails(cutoff30days);
        verificationResultRepository.nullifyExpiredEvidence(cutoff30days);
    }
}

