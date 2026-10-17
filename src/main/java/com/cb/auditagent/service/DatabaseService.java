package com.cb.auditagent.service;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.data.ConversationDataService;
import com.cb.auditagent.service.data.RemediationDataService;
import com.cb.auditagent.service.data.ScanDataService;
import com.cb.auditagent.service.data.UserDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Unified Facade for AuditAgent persistence operations.
 * Delegates domain-specific queries and mutations to dedicated domain data services:
 * - {@link ConversationDataService}
 * - {@link RemediationDataService}
 * - {@link UserDataService}
 * - {@link ScanDataService}
 */
@Service
public class DatabaseService {

    private final ConversationDataService conversationDataService;
    private final RemediationDataService remediationDataService;
    private final UserDataService userDataService;
    private final ScanDataService scanDataService;

    public DatabaseService(ConversationDataService conversationDataService,
                           RemediationDataService remediationDataService,
                           UserDataService userDataService,
                           ScanDataService scanDataService) {
        this.conversationDataService = conversationDataService;
        this.remediationDataService = remediationDataService;
        this.userDataService = userDataService;
        this.scanDataService = scanDataService;
    }

    public record GlobalPattern(String ruleId, String positivePattern, String negativePattern) {
    }

    public ConversationDataService getConversationDataService() {
        return conversationDataService;
    }

    public RemediationDataService getRemediationDataService() {
        return remediationDataService;
    }

    public UserDataService getUserDataService() {
        return userDataService;
    }

    public ScanDataService getScanDataService() {
        return scanDataService;
    }

    public void init() {
    }

    @Transactional
    public void withTransaction(Consumer<Connection> action) {
        action.accept(null);
    }

    public String normalizePath(String path) {
        return Path.of(path).normalize().toString();
    }

    // --- Scan & Findings Operations ---

    @Transactional
    public void saveReport(String repoPath, String mdReport, String htmlReport, List<Vulnerability> findings,
            ScanMetadata metadata) {
        scanDataService.saveReport(repoPath, mdReport, htmlReport, findings, metadata);
    }

    public Optional<Report> getReport(String repoPath) {
        return scanDataService.getReport(repoPath);
    }

    public Optional<Vulnerability> getVulnerabilityById(String vulnId) {
        return scanDataService.getVulnerabilityById(vulnId);
    }

    public Optional<Vulnerability> getVulnerabilityById(String vulnId, String repoPath) {
        return scanDataService.getVulnerabilityById(vulnId, repoPath);
    }

    @Transactional
    public void updateVulnerabilityStatus(String vulnId, VulnerabilityStatus status, String proposedFix) {
        scanDataService.updateVulnerabilityStatus(vulnId, status, proposedFix);
    }

    @Transactional
    public void updateVulnerabilityStatusTx(Connection conn, String vulnId, VulnerabilityStatus status,
            String proposedFix) throws SQLException {
        updateVulnerabilityStatus(vulnId, status, proposedFix);
    }

    @Transactional
    public void unused_updateVulnerabilityStatusTx(Connection conn, String vulnId, VulnerabilityStatus status,
            String proposedFix) {
        updateVulnerabilityStatus(vulnId, status, proposedFix);
    }

    @Transactional
    public void saveScanHistory(ScanHistoryRecord record) {
        scanDataService.saveScanHistory(record);
    }

    public List<ScanHistoryRecord> getScanHistory(long repositoryId, String branch) {
        return scanDataService.getScanHistory(repositoryId, branch);
    }

    @Transactional
    public void deleteScanHistory(long repositoryId, String branch) {
        scanDataService.deleteScanHistory(repositoryId, branch);
    }

    @Transactional
    public void upsertManagedRepository(ManagedRepository repository) {
        scanDataService.upsertManagedRepository(repository);
    }

    public Optional<ManagedRepository> getManagedRepository(long repositoryId) {
        return scanDataService.getManagedRepository(repositoryId);
    }

    @Transactional
    public void saveScanSnapshot(ScanSnapshot snapshot) {
        scanDataService.saveScanSnapshot(snapshot);
    }

    public Optional<ScanSnapshot> findLatestScanSnapshot(long repositoryId, String branch) {
        return scanDataService.findLatestScanSnapshot(repositoryId, branch);
    }

    public boolean recordWebhookDelivery(String deliveryId, String eventType) {
        return scanDataService.recordWebhookDelivery(deliveryId, eventType);
    }

    @Transactional
    public void releaseWebhookDelivery(String deliveryId) {
        scanDataService.releaseWebhookDelivery(deliveryId);
    }

    @Transactional
    public void saveTokenUsage(String threadId, String runId, String vulnerabilityId, String modelName, int tokens) {
        scanDataService.saveTokenUsage(threadId, runId, vulnerabilityId, modelName, tokens);
    }

    public List<Map<String, Object>> getTokenUsageForUser(String userId) {
        return scanDataService.getTokenUsageForUser(userId);
    }

    // --- Conversation & Memory Thread Operations ---

    public String getOrCreateMemoryThread(String clientId, String repoPath) {
        return conversationDataService.getOrCreateMemoryThread(clientId, repoPath);
    }

    public boolean memoryThreadExists(String threadId, String repoPath) {
        return conversationDataService.memoryThreadExists(threadId, repoPath);
    }

    @Transactional
    public void ensureMemoryThread(String threadId, String clientId, String repoPath) {
        conversationDataService.ensureMemoryThread(threadId, clientId, repoPath);
    }

    public boolean memoryThreadBelongsTo(String threadId, String userId) {
        return conversationDataService.memoryThreadBelongsTo(threadId, userId);
    }

    @Transactional
    public MemoryMessageRecord appendMemoryMessage(String threadId, String runId, String role, String messageType,
            String content, String metadataJson) {
        return conversationDataService.appendMemoryMessage(threadId, runId, role, messageType, content, metadataJson);
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId) {
        return conversationDataService.getMemoryMessages(threadId);
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId, String runId, boolean includeGeneralChat) {
        return conversationDataService.getMemoryMessages(threadId, runId, includeGeneralChat);
    }

    @Transactional
    public void deleteMemoryThread(String threadId) {
        remediationDataService.deleteRunsAndCheckpointsForThread(threadId);
        conversationDataService.deleteMemoryThread(threadId);
    }

    // --- Remediation & Agent Run Operations ---

    public Map<String, String> getVerificationEvidence(String runId) {
        return remediationDataService.getVerificationEvidence(runId);
    }

    @Transactional
    public void addVerificationResult(String runId, String kind, String status, String evidence, Integer exitCode) {
        remediationDataService.addVerificationResult(runId, kind, status, evidence, exitCode);
    }

    @Transactional
    public AgentRunRecord createAgentRun(String threadId, Vulnerability vulnerability, String repoPath,
            int maxIterations) {
        return remediationDataService.createAgentRun(threadId, vulnerability, repoPath, maxIterations);
    }

    @Transactional
    public AgentRunRecord createManagedAgentRun(String threadId, Vulnerability vulnerability, String workspacePath,
            int maxIterations) {
        return remediationDataService.createManagedAgentRun(threadId, vulnerability, workspacePath, maxIterations);
    }

    @Transactional
    public void updateAgentRun(String runId, AgentPhase phase, AgentRunStatus status, int iteration, int retryCount,
            boolean patchApplied, boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail) {
        remediationDataService.updateAgentRun(runId, phase, status, iteration, retryCount, patchApplied, compilePassed,
                rescanPassed, testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
    }

    @Transactional
    public void updateAgentRunTx(Connection conn, String runId, AgentPhase phase, AgentRunStatus status, int iteration,
            int retryCount, boolean patchApplied, boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail)
            throws SQLException {
        remediationDataService.updateAgentRunTx(conn, runId, phase, status, iteration, retryCount, patchApplied,
                compilePassed, rescanPassed, testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
    }

    @Transactional
    public void unused_updateAgentRunTx(Connection conn, String runId, AgentPhase phase, AgentRunStatus status,
            int iteration, int retryCount, boolean patchApplied, boolean compilePassed, boolean rescanPassed,
            boolean testsPassed, String checkpointJson, String finalSummary, String errorCode, String errorDetail) {
        remediationDataService.unused_updateAgentRunTx(conn, runId, phase, status, iteration, retryCount, patchApplied,
                compilePassed, rescanPassed, testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
    }

    public Optional<AgentRunRecord> getAgentRun(String runId) {
        return remediationDataService.getAgentRun(runId);
    }

    public Optional<AgentRunRecord> findRecoverableRun(String threadId) {
        return remediationDataService.findRecoverableRun(threadId);
    }

    public List<AgentRunRecord> findRecoverableRuns(String threadId) {
        return remediationDataService.findRecoverableRuns(threadId);
    }

    @Transactional
    public boolean appendAgentStep(String runId, int iteration, String action, String toolName, String arguments,
            String result, String resultStatus, long durationMs, String idempotencyKey) {
        return remediationDataService.appendAgentStep(runId, iteration, action, toolName, arguments, result,
                resultStatus, durationMs, idempotencyKey);
    }

    public boolean hasCompletedStep(String runId, String idempotencyKey) {
        return remediationDataService.hasCompletedStep(runId, idempotencyKey);
    }

    @Transactional
    public void addRunChange(String runId, String filePath, String beforeHash, String afterHash, String backupPath) {
        remediationDataService.addRunChange(runId, filePath, beforeHash, afterHash, backupPath);
    }

    public List<RunChangeRecord> getRunChanges(String runId) {
        return remediationDataService.getRunChanges(runId);
    }

    @Transactional
    public void markRunChangeState(String changeId, String state) {
        remediationDataService.markRunChangeState(changeId, state);
    }

    @Transactional
    public void saveApprovedRepositoryMemory(String repoPath, Vulnerability vulnerability, String runId,
            String summary) {
        remediationDataService.saveApprovedRepositoryMemory(repoPath, vulnerability, runId, summary);
    }

    @Transactional
    public void saveApprovedRepositoryMemoryTx(Connection conn, String repoPath, Vulnerability vulnerability,
            String runId, String summary) throws SQLException {
        remediationDataService.saveApprovedRepositoryMemoryTx(conn, repoPath, vulnerability, runId, summary);
    }

    @Transactional
    public void unused_saveApprovedRepositoryMemoryTx(Connection conn, String repoPath, Vulnerability vulnerability,
            String runId, String summary) {
        remediationDataService.unused_saveApprovedRepositoryMemoryTx(conn, repoPath, vulnerability, runId, summary);
    }

    @Transactional
    public void saveRejectedRepositoryMemory(String runId, String reason) {
        remediationDataService.saveRejectedRepositoryMemory(runId, reason);
    }

    @Transactional
    public void lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) throws SQLException {
        remediationDataService.lowerRepositoryMemoryConfidenceTx(conn, reportKey, vulnerability, runId);
    }

    @Transactional
    public void unused_lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) {
        remediationDataService.unused_lowerRepositoryMemoryConfidenceTx(conn, reportKey, vulnerability, runId);
    }

    @Transactional
    public void saveGlobalPatternTx(Connection conn, String ruleId, String positivePattern, String negativePattern,
            String runId) throws SQLException {
        remediationDataService.saveGlobalPatternTx(conn, ruleId, positivePattern, negativePattern, runId);
    }

    @Transactional
    public void unused_saveGlobalPatternTx(Connection conn, String ruleId, String positivePattern,
            String negativePattern, String runId) {
        remediationDataService.unused_saveGlobalPatternTx(conn, ruleId, positivePattern, negativePattern, runId);
    }

    public List<RepositoryMemory> searchRepositoryMemories(String repoPath, Vulnerability vulnerability) {
        return remediationDataService.searchRepositoryMemories(repoPath, vulnerability);
    }

    public List<RepositoryMemory> searchRejectedMemories(String repoPath, Vulnerability vulnerability) {
        return remediationDataService.searchRejectedMemories(repoPath, vulnerability);
    }

    @Transactional
    public void deleteRepositoryMemories(String repoPath) {
        remediationDataService.deleteRepositoryMemories(repoPath);
    }

    @Transactional
    public void ensureFtsIndex() {
        remediationDataService.ensureFtsIndex();
    }

    public boolean isFtsAvailable() {
        return remediationDataService.isFtsAvailable();
    }

    public Optional<DatabaseService.GlobalPattern> getGlobalPattern(String ruleId) {
        return remediationDataService.getGlobalPattern(ruleId)
                .map(pattern -> new GlobalPattern(pattern.ruleId(), pattern.positivePattern(), pattern.negativePattern()));
    }

    @Transactional
    public void createRunPublication(RunPublicationRecord publication) {
        remediationDataService.createRunPublication(publication);
    }

    @Transactional
    public void saveRunPublication(RunPublicationRecord publication) {
        remediationDataService.saveRunPublication(publication);
    }

    @Transactional
    public void saveRunPublicationTx(Connection conn, RunPublicationRecord publication) throws SQLException {
        remediationDataService.saveRunPublicationTx(conn, publication);
    }

    @Transactional
    public void unused_saveRunPublicationTx(Connection conn, RunPublicationRecord publication) {
        remediationDataService.unused_saveRunPublicationTx(conn, publication);
    }

    public Optional<RunPublicationRecord> getRunPublication(String runId) {
        return remediationDataService.getRunPublication(runId);
    }

    public boolean hasActiveManagedRun(long repositoryId, String branch, String fingerprint) {
        return remediationDataService.hasActiveManagedRun(repositoryId, branch, fingerprint);
    }

    public List<String> getActiveManagedRunIds(long repositoryId, String branch, String fingerprint) {
        return remediationDataService.getActiveManagedRunIds(repositoryId, branch, fingerprint);
    }

    public Optional<RunPublicationRecord> findRunPublication(long repositoryId, int prNumber) {
        return remediationDataService.findRunPublication(repositoryId, prNumber);
    }

    public List<RunPublicationRecord> findStaleWorkspaces(List<String> terminalStatuses, LocalDateTime before) {
        return remediationDataService.findStaleWorkspaces(terminalStatuses, before);
    }

    @Transactional
    public void saveGraphCheckpoint(String checkpointId, String runId, String threadId, String parentId,
            String nodeName, String stateJson, String interruptReason) {
        remediationDataService.saveGraphCheckpoint(checkpointId, runId, threadId, parentId, nodeName, stateJson,
                interruptReason);
    }

    public String[] loadGraphCheckpoint(String checkpointId) {
        return remediationDataService.loadGraphCheckpoint(checkpointId);
    }

    @Transactional
    public void deleteGraphCheckpoints(String workflowId) {
        remediationDataService.deleteGraphCheckpoints(workflowId);
    }

    public String loadLatestGraphCheckpoint(String runId) {
        return remediationDataService.loadLatestGraphCheckpoint(runId);
    }

    public List<String> listGraphCheckpoints(String threadId) {
        return remediationDataService.listGraphCheckpoints(threadId);
    }

    @Transactional
    public void updateAgentRunCheckpointJson(String runId, String json) {
        remediationDataService.updateAgentRunCheckpointJson(runId, json);
    }

    @Transactional
    public void saveNodeEvent(String eventId, String runId, String checkpointId, String nodeName, String status,
            Long durationMs, String detailJson) {
        remediationDataService.saveNodeEvent(eventId, runId, checkpointId, nodeName, status, durationMs, detailJson);
    }

    public List<String> getNodeEvents(String runId) {
        return remediationDataService.getNodeEvents(runId);
    }

    @Transactional
    public void saveSubagentState(String stateId, String runId, String parentCheckpointId, String childAgent,
            int sequenceNumber, String contextJson, String resultJson, String status) {
        remediationDataService.saveSubagentState(stateId, runId, parentCheckpointId, childAgent, sequenceNumber,
                contextJson, resultJson, status);
    }

    @Transactional
    public void updateSubagentResult(String stateId, String resultJson, String status) {
        remediationDataService.updateSubagentResult(stateId, resultJson, status);
    }

    public String getSubagentContext(String runId, String childAgent) {
        return remediationDataService.getSubagentContext(runId, childAgent);
    }

    // --- User & Authentication Operations ---

    @Transactional
    public String upsertGitHubUser(long githubUserId, String login, String displayName, String avatarUrl) {
        return userDataService.upsertGitHubUser(githubUserId, login, displayName, avatarUrl);
    }

    @Transactional
    public void saveGitHubAuthorization(GitHubAuthorization authorization) {
        userDataService.saveGitHubAuthorization(authorization);
    }

    public Optional<GitHubAuthorization> getGitHubAuthorization(String userId) {
        return userDataService.getGitHubAuthorization(userId);
    }

    @Transactional
    public void saveOAuthState(String stateHash, String verifierEncrypted, LocalDateTime expiresAt) {
        userDataService.saveOAuthState(stateHash, verifierEncrypted, expiresAt);
    }

    public Optional<String> consumeOAuthState(String stateHash) {
        return userDataService.consumeOAuthState(stateHash);
    }

    @Transactional
    public void createUserSession(String sessionHash, String userId, String csrfToken, LocalDateTime expiresAt) {
        userDataService.createUserSession(sessionHash, userId, csrfToken, expiresAt);
    }

    public Optional<AuthenticatedUser> findAuthenticatedUser(String sessionHash) {
        return userDataService.findAuthenticatedUser(sessionHash);
    }

    @Transactional
    public void deleteUserSession(String sessionHash) {
        userDataService.deleteUserSession(sessionHash);
    }

    @Transactional
    public void saveNotification(Notification notification) {
        userDataService.saveNotification(notification);
    }

    public List<Notification> getNotifications(String userId) {
        return userDataService.getNotifications(userId);
    }

    @Transactional
    public void markNotificationsRead(String userId) {
        userDataService.markNotificationsRead(userId);
    }

    // --- Retention & Expiration ---

    @Transactional
    public void deleteExpiredMemory() {
        LocalDateTime now = LocalDateTime.now();
        userDataService.deleteExpiredMemory(now);
        conversationDataService.deleteExpiredMemory(now);
        remediationDataService.deleteExpiredMemory(now, now.minusDays(30));
    }
}
