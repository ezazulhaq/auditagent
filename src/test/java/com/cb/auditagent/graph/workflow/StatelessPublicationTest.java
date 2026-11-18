package com.cb.auditagent.graph.workflow;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.*;
import com.cb.auditagent.service.storage.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatelessPublicationTest {

    @TempDir
    Path tempDir;

    private DatabaseService database;
    private GitHubApiClient github;
    private PullRequestProvider pullRequests;
    private SourceControlProvider sourceControl;
    private ReporterService reporter;
    private SupabaseStorageService storageService;
    private PatchBundleService patchBundleService;
    private AgentConfig agentConfig;

    private final String runId = "run-stateless-999";
    private final String vulnId = "VULN-00042";
    private final long repoId = 101L;
    private final long installationId = 77L;
    private final String reportKey = "github:101:main";
    private final String baseSha = "sha-base-123456";
    private final String branchName = "auditagent/fix-VULN-00042";
    private final AuthenticatedUser user = new AuthenticatedUser("github:42", 42, "dev-user", "Dev User", "", "csrf");
    private final ManagedRepository repository = new ManagedRepository(
            repoId, installationId, "org", "repo", "org/repo",
            "https://github.test/org/repo.git", "main", true, "WRITE"
    );

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        github = mock(GitHubApiClient.class);
        pullRequests = mock(PullRequestProvider.class);
        sourceControl = mock(SourceControlProvider.class);
        reporter = mock(ReporterService.class);
        storageService = mock(SupabaseStorageService.class);
        agentConfig = new AgentConfig();
        agentConfig.setStatelessWorkspaces(true);

        patchBundleService = new PatchBundleService(storageService, database, sourceControl, new ObjectMapper());

        when(database.getManagedRepository(repoId)).thenReturn(Optional.of(repository));
        when(github.installationToken(installationId)).thenReturn("ghs_mock_token");
        when(github.installationHasWorkflowPermission(installationId)).thenReturn(true);
    }

    @Test
    @DisplayName("Remediate on Node A, upload patch bundle, delete Node A workspace, publish successfully on Node B")
    void remediateOnNodeA_publishOnNodeB_usingOnlyRemotePatchBundle() throws Exception {
        // --- STEP 1: Node A creates a workspace, fixes vulnerability, creates and uploads PatchBundle ---
        Path nodeAWorkspace = Files.createDirectory(tempDir.resolve("node-a-workspace"));
        Path sourceFile = Files.createDirectories(nodeAWorkspace.resolve("src")).resolve("Service.java");
        Files.writeString(sourceFile, "public class Service { /* fixed code */ }");

        when(sourceControl.changedFiles(nodeAWorkspace)).thenReturn(List.of("src/Service.java"));
        when(sourceControl.workspaceDiff(nodeAWorkspace)).thenReturn("diff --git a/src/Service.java b/src/Service.java\n+ /* fixed code */");
        when(sourceControl.changedFileHashes(nodeAWorkspace)).thenReturn(Map.of("src/Service.java", "hash-fixed-999"));

        // Capture uploaded bundle JSON from Node A
        ArgumentCaptor<String> bundleJsonCaptor = ArgumentCaptor.forClass(String.class);
        when(storageService.uploadTextArtifact(eq("runs/" + runId + "/patch-bundle.json"), bundleJsonCaptor.capture()))
                .thenReturn("storage://bucket/runs/" + runId + "/patch-bundle.json");

        PatchBundle uploadedBundle = patchBundleService.createAndUploadBundle(
                runId, vulnId, repoId, "main", baseSha, branchName, nodeAWorkspace, "Applied security patch"
        );
        assertNotNull(uploadedBundle);
        String capturedJson = bundleJsonCaptor.getValue();
        assertNotNull(capturedJson);

        // --- STEP 2: Node A deletes its workspace (simulating pod termination or stateless cleanup) ---
        Files.deleteIfExists(sourceFile);
        Files.deleteIfExists(nodeAWorkspace.resolve("src"));
        Files.deleteIfExists(nodeAWorkspace);
        assertFalse(Files.exists(nodeAWorkspace), "Node A workspace must be completely removed");

        // --- STEP 3: Node B receives approval decision and publishes ---
        // Setup database state for publication
        AgentRunRecord runRecord = new AgentRunRecord(
                runId, "thread-1", vulnId, "fingerprint",
                nodeAWorkspace.toString(), AgentPhase.AWAITING_APPROVAL, AgentRunStatus.AWAITING_APPROVAL,
                1, 0, 15, true, true, true, true, null,
                "Applied security patch", null, null, LocalDateTime.now()
        );
        RunPublicationRecord publicationRecord = new RunPublicationRecord(
                runId, user.userId(), repoId, installationId, reportKey,
                "main", baseSha, nodeAWorkspace.toString(), branchName,
                "approval-digest", user.login(), LocalDateTime.now(),
                null, "NOT_STARTED", null, null, null, null
        );
        Vulnerability vulnerability = new Vulnerability(
                vulnId, "src/Service.java", 1, "public class Service {}",
                Severity.HIGH, "SQL Injection", "Unsafe query", "java"
        );
        vulnerability.setStatus(VulnerabilityStatus.AWAITING_APPROVAL);

        when(database.getAgentRun(runId)).thenReturn(Optional.of(runRecord));
        when(database.getRunPublication(runId)).thenReturn(Optional.of(publicationRecord));
        when(database.getVulnerabilityById(vulnId)).thenReturn(Optional.of(vulnerability));
        when(database.getVulnerabilityById(vulnId, reportKey)).thenReturn(Optional.of(vulnerability));

        // When Node B downloads the patch bundle, return Node A's uploaded JSON
        when(storageService.downloadTextArtifact("runs/" + runId + "/patch-bundle.json")).thenReturn(capturedJson);

        // Node B shallow-clones into ephemeral workspace
        Path nodeBEphemeralWorkspace = Files.createDirectory(tempDir.resolve("node-b-ephemeral"));
        when(sourceControl.cloneAtCommit(eq(repository), eq("main"), eq(baseSha), eq("ghs_mock_token"), anyString()))
                .thenReturn(nodeBEphemeralWorkspace);
        when(sourceControl.changedFiles(nodeBEphemeralWorkspace)).thenReturn(List.of("src/Service.java"));

        // Pull request publishing on Node B returns PR #42
        PublishResult publishResult = new PublishResult(
                branchName, "commit-sha-published", 42, "https://github.test/org/repo/pull/42"
        );
        when(pullRequests.publish(eq(repository), eq(nodeBEphemeralWorkspace), eq("main"), eq(branchName),
                eq("ghs_mock_token"), eq(user.login()), eq(runId), eq(vulnerability), anyString(), any()))
                .thenReturn(publishResult);

        // Instantiate PublicationNode on Node B
        PublicationNode nodeBPublicationNode = new PublicationNode(
                database, github, pullRequests, sourceControl, reporter, patchBundleService, agentConfig
        );

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("runId", runId);
        stateData.put("authenticatedUser", user);
        stateData.put("decision", "APPROVE_AND_CREATE_PR");
        WorkflowState state = new WorkflowState(stateData);

        // Execute publication on Node B
        Map<String, Object> result = nodeBPublicationNode.apply(state);

        // --- STEP 4: Assertions ---
        assertNotNull(result);
        assertEquals("PUBLISHED", result.get("runStatus"));
        assertEquals(42, result.get("pullRequestNumber"));
        assertEquals("https://github.test/org/repo/pull/42", result.get("pullRequestUrl"));

        // Verify clean clone and bundle application occurred on Node B
        verify(sourceControl).cloneAtCommit(eq(repository), eq("main"), eq(baseSha), eq("ghs_mock_token"), anyString());
        assertTrue(Files.exists(nodeBEphemeralWorkspace.resolve("src/Service.java")),
                "PatchBundle must have reconstructed the modified file in the ephemeral workspace");
        assertEquals("public class Service { /* fixed code */ }",
                Files.readString(nodeBEphemeralWorkspace.resolve("src/Service.java")));

        // Verify Node B ephemeral workspace was cleaned up at the end of publication
        verify(sourceControl).cleanupWorkspace(nodeBEphemeralWorkspace);

        // Verify vulnerability status updated to PR_OPEN
        assertEquals(VulnerabilityStatus.PR_OPEN, vulnerability.getStatus());
        verify(database).updateVulnerabilityStatus(eq(vulnId), eq(VulnerabilityStatus.PR_OPEN), any());
    }
}
