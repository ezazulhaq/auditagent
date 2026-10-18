package com.cb.auditagent.graph.workflow;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.SourceControlProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalReadyNodeTest {

    @TempDir
    Path tempDir;

    private DatabaseService database;
    private SourceControlProvider sourceControl;
    private ApprovalReadyNode approvalReadyNode;

    private final String runId = "run-123456";
    private final String vulnId = "VULN-000001";
    private final String threadId = "thread-1";
    private final String reportKey = "github:42:main";
    private final AuthenticatedUser user = new AuthenticatedUser("github:99", 99, "octocat", "Octo", "", "csrf");
    private final ManagedRepository repository = new ManagedRepository(42, 9, "octo", "repo", "octo/repo",
            "https://github.test/octo/repo.git", "main", true, "WRITE");

    private Path workspacePath;
    private RunPublicationRecord publication;
    private Vulnerability vulnerability;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        sourceControl = mock(SourceControlProvider.class);
        approvalReadyNode = new ApprovalReadyNode(database, sourceControl);

        workspacePath = tempDir.resolve("workspace");
        publication = new RunPublicationRecord(runId, user.userId(), 42L, 9L, reportKey,
                "main", "base-sha", workspacePath.toString(), "auditagent/fix-123",
                null, null, null, null, "NOT_STARTED", null, null, null, null);

        vulnerability = new Vulnerability(vulnId, "src/UserDao.java", 10, "unsafe",
                Severity.HIGH, "SQL Injection", "Unsafe query", "java");
        vulnerability.setStatus(VulnerabilityStatus.ANALYZING);

        when(database.getRunPublication(runId)).thenReturn(Optional.of(publication));
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(repository));
        when(database.getVulnerabilityById(vulnId, reportKey)).thenReturn(Optional.of(vulnerability));
        when(database.getVulnerabilityById(vulnId)).thenReturn(Optional.of(vulnerability));
    }

    @Test
    @DisplayName("When files are empty and run aborted with ABORTED error code, marks PATCH_FAILED and throws")
    void whenFilesEmpty_andRunAborted_marksPatchFailedAndThrows() {
        AgentRunRecord run = new AgentRunRecord(runId, threadId, vulnId, "fingerprint",
                workspacePath.toString(), AgentPhase.FAILED, AgentRunStatus.FAILED,
                5, 0, 5, false, false, false, false, null,
                "Agent aborted after reaching max iterations or max consecutive errors.",
                "ABORTED", null, LocalDateTime.now());
        when(database.getAgentRun(runId)).thenReturn(Optional.of(run));
        when(sourceControl.changedFiles(workspacePath)).thenReturn(List.of());

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        stateData.put("nextAgent", "FAIL");
        WorkflowState state = new WorkflowState(stateData);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> approvalReadyNode.apply(state));

        assertTrue(ex.getMessage().contains("PATCH_FAILED"), "Error message should mention PATCH_FAILED: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Agent aborted after reaching max iterations"),
                "Error message should mention failure reason: " + ex.getMessage());

        verify(database).updateAgentRun(eq(runId), eq(AgentPhase.FAILED), eq(AgentRunStatus.FAILED),
                eq(5), eq(0), eq(false), eq(false), eq(false), eq(false),
                isNull(), eq("Agent aborted after reaching max iterations or max consecutive errors."),
                eq("ABORTED"), anyString());

        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.PATCH_FAILED),
                eq("Agent aborted after reaching max iterations or max consecutive errors."));
    }

    @Test
    @DisplayName("When files are empty and agent explicitly asserts false positive, marks IGNORED and throws")
    void whenFilesEmpty_andExplicitFalsePositive_marksIgnoredAndThrows() {
        AgentRunRecord run = new AgentRunRecord(runId, threadId, vulnId, "fingerprint",
                workspacePath.toString(), AgentPhase.PLANNING, AgentRunStatus.ACTIVE,
                1, 0, 15, false, false, false, false, null,
                "The vulnerability is a false positive. No fix needed.",
                null, null, LocalDateTime.now());
        when(database.getAgentRun(runId)).thenReturn(Optional.of(run));
        when(sourceControl.changedFiles(workspacePath)).thenReturn(List.of());

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        stateData.put("finalResponse", "The vulnerability is a false positive. No fix needed.");
        WorkflowState state = new WorkflowState(stateData);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> approvalReadyNode.apply(state));

        assertTrue(ex.getMessage().contains("IGNORED"), "Error message should mention IGNORED: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("false positive"), "Error message should mention false positive: " + ex.getMessage());

        verify(database).updateAgentRun(eq(runId), eq(AgentPhase.REJECTED), eq(AgentRunStatus.FAILED),
                eq(1), eq(0), eq(false), eq(false), eq(false), eq(false),
                isNull(), eq("The vulnerability is a false positive. No fix needed."),
                eq("FALSE_POSITIVE"), isNull());

        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.IGNORED), any());
    }

    @Test
    @DisplayName("When files are empty and no changes generated without false positive claim, marks PATCH_FAILED")
    void whenFilesEmpty_andNoFixProducedWithoutExplicitFalsePositive_marksPatchFailed() {
        AgentRunRecord run = new AgentRunRecord(runId, threadId, vulnId, "fingerprint",
                workspacePath.toString(), AgentPhase.PLANNING, AgentRunStatus.ACTIVE,
                3, 0, 15, false, false, false, false, null,
                "Unable to generate valid patch for this vulnerability",
                null, null, LocalDateTime.now());
        when(database.getAgentRun(runId)).thenReturn(Optional.of(run));
        when(sourceControl.changedFiles(workspacePath)).thenReturn(List.of());

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        stateData.put("finalResponse", "Unable to generate valid patch for this vulnerability");
        WorkflowState state = new WorkflowState(stateData);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> approvalReadyNode.apply(state));

        assertTrue(ex.getMessage().contains("PATCH_FAILED"), "Error message should mention PATCH_FAILED: " + ex.getMessage());

        verify(database).updateAgentRun(eq(runId), eq(AgentPhase.FAILED), eq(AgentRunStatus.FAILED),
                eq(3), eq(0), eq(false), eq(false), eq(false), eq(false),
                isNull(), eq("Unable to generate valid patch for this vulnerability"),
                eq("NO_CHANGES_GENERATED"), anyString());

        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.PATCH_FAILED),
                eq("Unable to generate valid patch for this vulnerability"));
    }

    @Test
    @DisplayName("When files are empty and text contains negation 'not a false positive', marks PATCH_FAILED")
    void whenFilesEmpty_andSummarySaysNotAFalsePositive_marksPatchFailed() {
        AgentRunRecord run = new AgentRunRecord(runId, threadId, vulnId, "fingerprint",
                workspacePath.toString(), AgentPhase.PLANNING, AgentRunStatus.ACTIVE,
                2, 0, 15, false, false, false, false, null,
                "This is not a false positive, but I could not find a safe patch.",
                null, null, LocalDateTime.now());
        when(database.getAgentRun(runId)).thenReturn(Optional.of(run));
        when(sourceControl.changedFiles(workspacePath)).thenReturn(List.of());

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        stateData.put("finalResponse", "This is not a false positive, but I could not find a safe patch.");
        WorkflowState state = new WorkflowState(stateData);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> approvalReadyNode.apply(state));

        assertTrue(ex.getMessage().contains("PATCH_FAILED"), "Error message should mention PATCH_FAILED: " + ex.getMessage());

        verify(database).updateAgentRun(eq(runId), eq(AgentPhase.FAILED), eq(AgentRunStatus.FAILED),
                eq(2), eq(0), eq(false), eq(false), eq(false), eq(false),
                isNull(), eq("This is not a false positive, but I could not find a safe patch."),
                eq("NO_CHANGES_GENERATED"), anyString());

        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.PATCH_FAILED), any());
    }

    @Test
    @DisplayName("When files are present, generates ApprovalPreview and sets AWAITING_APPROVAL")
    void whenFilesPresent_generatesApprovalPreviewAndSetsAwaitingApproval() throws Exception {
        AgentRunRecord run = new AgentRunRecord(runId, threadId, vulnId, "fingerprint",
                workspacePath.toString(), AgentPhase.VERIFYING, AgentRunStatus.ACTIVE,
                6, 0, 15, true, true, true, true, null,
                "Verified security fix applied.",
                null, null, LocalDateTime.now());
        when(database.getAgentRun(runId)).thenReturn(Optional.of(run));
        when(sourceControl.changedFiles(workspacePath)).thenReturn(List.of("src/UserDao.java"));
        when(sourceControl.workspaceDiff(workspacePath)).thenReturn("diff --git a/UserDao.java...");
        when(sourceControl.changedFileHashes(workspacePath)).thenReturn(Map.of("src/UserDao.java", "abc123hash"));
        when(database.getVerificationEvidence(runId)).thenReturn(Map.of("compile", "PASSED", "rescan", "PASSED"));

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        WorkflowState state = new WorkflowState(stateData);

        Map<String, Object> result = approvalReadyNode.apply(state);

        assertNotNull(result);
        assertNotNull(result.get("approvalPreview"));
        ApprovalPreview preview = (ApprovalPreview) result.get("approvalPreview");
        assertEquals(runId, preview.runId());
        assertEquals(List.of("src/UserDao.java"), preview.changedFiles());

        verify(database).updateAgentRun(eq(runId), eq(AgentPhase.AWAITING_APPROVAL),
                eq(AgentRunStatus.AWAITING_APPROVAL), eq(6), eq(0), eq(true), eq(true), eq(true), eq(true),
                isNull(), eq("Verified security fix applied."), isNull(), isNull());

        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.AWAITING_APPROVAL),
                eq("Verified security fix applied."));
    }
}
