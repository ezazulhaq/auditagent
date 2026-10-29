package com.cb.auditagent.service;

import com.cb.auditagent.domain.*;
import com.cb.auditagent.service.data.ConversationDataService;
import com.cb.auditagent.service.data.RemediationDataService;
import com.cb.auditagent.service.data.ScanDataService;
import com.cb.auditagent.service.data.UserDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainDataServicesTest {

    @MockitoBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @MockitoBean
    private dev.langchain4j.model.chat.ChatModel agentModel;

    @Autowired
    private ConversationDataService conversationDataService;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ScanDataService scanDataService;

    @Autowired
    private RemediationDataService remediationDataService;

    @Test
    void testConversationDataService() {
        String clientId = "user-123";
        String repoPath = "owner/repo";

        String threadId = conversationDataService.getOrCreateMemoryThread(clientId, repoPath);
        assertNotNull(threadId);
        assertTrue(conversationDataService.memoryThreadExists(threadId, repoPath));
        assertTrue(conversationDataService.memoryThreadBelongsTo(threadId, clientId));

        MemoryMessageRecord msg1 = conversationDataService.appendMemoryMessage(
                threadId, null, "user", "text", "Fix this issue", "{}");
        assertNotNull(msg1);
        assertEquals(threadId, msg1.threadId());
        assertEquals("user", msg1.role());

        List<MemoryMessageRecord> messages = conversationDataService.getMemoryMessages(threadId);
        assertEquals(1, messages.size());
        assertEquals("Fix this issue", messages.get(0).content());

        conversationDataService.deleteMemoryThread(threadId);
        assertFalse(conversationDataService.memoryThreadExists(threadId, repoPath));
        assertTrue(conversationDataService.getMemoryMessages(threadId).isEmpty());
    }

    @Test
    void testUserDataService() {
        long ghId = 12345L;
        String userId = userDataService.upsertGitHubUser(ghId, "devuser", "Dev User", "https://avatar.url");
        assertNotNull(userId);

        LocalDateTime future = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
        userDataService.saveGitHubAuthorization(new GitHubAuthorization(userId, "enc-acc", future, "enc-ref", future));
        Optional<GitHubAuthorization> authOpt = userDataService.getGitHubAuthorization(userId);
        assertTrue(authOpt.isPresent());
        assertEquals("enc-acc", authOpt.get().accessTokenEncrypted());

        String sessionHash = "hash-123";
        String csrfToken = "csrf-456";
        userDataService.createUserSession(sessionHash, userId, csrfToken, future);

        Optional<AuthenticatedUser> userOpt = userDataService.findAuthenticatedUser(sessionHash);
        assertTrue(userOpt.isPresent());
        assertEquals(userId, userOpt.get().userId());
        assertEquals(csrfToken, userOpt.get().csrfToken());

        Notification notif = new Notification();
        notif.setId("notif-1");
        notif.setUserId(userId);
        notif.setType("INFO");
        notif.setMessage("Test notification");
        userDataService.saveNotification(notif);

        List<Notification> notifs = userDataService.getNotifications(userId);
        assertEquals(1, notifs.size());
        assertEquals("Test notification", notifs.get(0).getMessage());
        assertFalse(notifs.get(0).isRead());

        userDataService.markNotificationsRead(userId);
        List<Notification> updatedNotifs = userDataService.getNotifications(userId);
        assertEquals(1, updatedNotifs.size());
        assertTrue(updatedNotifs.get(0).isRead());
        userDataService.deleteUserSession(sessionHash);
        assertTrue(userDataService.findAuthenticatedUser(sessionHash).isEmpty());
    }

    @Test
    void testScanDataService() {
        String repoPath = "repo/scan-test";
        Vulnerability vuln = new Vulnerability("VULN-SCAN-1", "src/App.java", 42, "password = '123'",
                Severity.HIGH, "hardcoded-secret", "Secret detected", "java");
        vuln.setRuleId("rules.secret");

        scanDataService.saveReport(repoPath, "# Report", "<p>Report</p>", List.of(vuln), new ScanMetadata());

        Optional<Report> reportOpt = scanDataService.getReport(repoPath);
        assertTrue(reportOpt.isPresent());
        assertEquals(repoPath, reportOpt.get().getRepoPath());

        Optional<Vulnerability> vulnOpt = scanDataService.getVulnerabilityById(vuln.getId());
        assertTrue(vulnOpt.isPresent());
        assertEquals("hardcoded-secret", vulnOpt.get().getVulnType());

        scanDataService.updateVulnerabilityStatus(vuln.getId(), VulnerabilityStatus.FIXED, "fix applied");
        Vulnerability updated = scanDataService.getVulnerabilityById(vuln.getId()).orElseThrow();
        assertEquals(VulnerabilityStatus.FIXED, updated.getStatus());
        assertEquals("fix applied", updated.getProposedFix());

        ManagedRepository managedRepo = new ManagedRepository(
                777L, 888L, "org", "service", "org/service", "https://github.com/org/service.git",
                "main", false, "ADMIN");
        scanDataService.upsertManagedRepository(managedRepo);

        Optional<ManagedRepository> fetchedRepo = scanDataService.getManagedRepository(777L);
        assertTrue(fetchedRepo.isPresent());
        assertEquals("org/service", fetchedRepo.get().fullName());
    }

    @Test
    void testRemediationDataService() {
        String threadId = conversationDataService.getOrCreateMemoryThread("test-client", "test/remediation");
        Vulnerability vuln = new Vulnerability("VULN-REM-1", "src/Query.java", 15, "SELECT *",
                Severity.HIGH, "sql-injection", "SQLi", "java");
        vuln.setRuleId("rules.sqli");
        scanDataService.saveReport("test/remediation", "md", "html", List.of(vuln), new ScanMetadata());

        AgentRunRecord run = remediationDataService.createAgentRun(threadId, vuln, "test/remediation", 5);
        assertNotNull(run);
        assertEquals(AgentRunStatus.ACTIVE, run.status());

        boolean stepAdded = remediationDataService.appendAgentStep(
                run.runId(), 1, "PLAN", "planner", "{}", "plan generated", "SUCCESS", 150L, "idemp-1");
        assertTrue(stepAdded);
        assertTrue(remediationDataService.hasCompletedStep(run.runId(), "idemp-1"));

        remediationDataService.addVerificationResult(run.runId(), "COMPILE", "SUCCESS", "Build passed", 0);
        assertEquals("Build passed", remediationDataService.getVerificationEvidence(run.runId()).get("COMPILE"));

        remediationDataService.addRunChange(run.runId(), "src/Query.java", "hashA", "hashB", "/backup/Query.java");
        List<RunChangeRecord> changes = remediationDataService.getRunChanges(run.runId());
        assertEquals(1, changes.size());
        assertEquals("src/Query.java", changes.get(0).filePath());

        remediationDataService.saveGraphCheckpoint("chk-1", run.runId(), threadId, null, "node_start", "{\"k\":\"v\"}", null);
        String[] loaded = remediationDataService.loadGraphCheckpoint("chk-1");
        assertEquals(2, loaded.length);
        assertEquals("{\"k\":\"v\"}", loaded[0]);
        assertEquals("node_start", loaded[1]);

        // Verify checkpoint can be saved without an active run (e.g., initial graph checkpoint before run creation)
        String workflowId = threadId + "-VULN-REM-1";
        remediationDataService.saveGraphCheckpoint("chk-init", null, workflowId, null, "validate_access", "{\"step\":\"init\"}", null);
        String[] loadedInit = remediationDataService.loadGraphCheckpoint("chk-init");
        assertEquals(2, loadedInit.length);
        assertEquals("{\"step\":\"init\"}", loadedInit[0]);
        assertEquals(List.of("chk-init"), remediationDataService.listGraphCheckpoints(workflowId));

        remediationDataService.saveNodeEvent("ev-1", run.runId(), "chk-1", "node_start", "SUCCESS", 25L, "{\"detail\":1}");
        List<String> events = remediationDataService.getNodeEvents(run.runId());
        assertEquals(1, events.size());
        assertEquals("{\"detail\":1}", events.get(0));

        remediationDataService.saveApprovedRepositoryMemory("test/remediation", vuln, run.runId(), "Parameterize queries");
        List<RepositoryMemory> memories = remediationDataService.searchRepositoryMemories("test/remediation", vuln);
        assertNotNull(memories);
    }

    @Test
    void testCreateManagedAgentRunAllowsConcurrentRunsInSameThread() {
        String threadId = conversationDataService.getOrCreateMemoryThread("test-client-mng", "test/managed");
        Vulnerability vuln1 = new Vulnerability("VULN-MNG-1", "src/A.java", 10, "code1",
                Severity.HIGH, "sqli", "SQLi 1", "java");
        vuln1.setFindingFingerprint("fp-1");
        Vulnerability vuln2 = new Vulnerability("VULN-MNG-2", "src/B.java", 20, "code2",
                Severity.MEDIUM, "xss", "XSS 2", "java");
        vuln2.setFindingFingerprint("fp-2");
        scanDataService.saveReport("test/managed", "md", "html", List.of(vuln1, vuln2), new ScanMetadata());

        AgentRunRecord run1 = remediationDataService.createManagedAgentRun(threadId, vuln1, "/workspaces/run-1", 10);
        assertNotNull(run1);
        assertEquals(AgentRunStatus.ACTIVE, run1.status());

        // Concurrently creating managed run 2 for a different vulnerability under the same threadId MUST succeed
        AgentRunRecord run2 = remediationDataService.createManagedAgentRun(threadId, vuln2, "/workspaces/run-2", 10);
        assertNotNull(run2);
        assertEquals(AgentRunStatus.ACTIVE, run2.status());

        // Both runs should be recoverable in this thread
        List<AgentRunRecord> activeRuns = remediationDataService.findRecoverableRuns(threadId);
        assertEquals(2, activeRuns.size());

        // Duplicate run for the SAME vulnerability or fingerprint should be rejected
        IllegalStateException exVuln = assertThrows(IllegalStateException.class, () ->
                remediationDataService.createManagedAgentRun(threadId, vuln1, "/workspaces/run-3", 10));
        assertTrue(exVuln.getMessage().contains("already active for this vulnerability or workspace"));

        // Duplicate run for the SAME workspace path should be rejected
        Vulnerability vuln3 = new Vulnerability("VULN-MNG-3", "src/C.java", 30, "code3",
                Severity.LOW, "info", "Info 3", "java");
        vuln3.setFindingFingerprint("fp-3");
        IllegalStateException exWs = assertThrows(IllegalStateException.class, () ->
                remediationDataService.createManagedAgentRun(threadId, vuln3, "/workspaces/run-1", 10));
        assertTrue(exWs.getMessage().contains("already active for this vulnerability or workspace"));
    }

    @Test
    void testTrackedIssuePersistenceWithManagedRepository() {
        long repoId = 999L;
        ManagedRepository repo = new ManagedRepository(
                repoId, 1000L, "testorg", "testrepo", "testorg/testrepo",
                "https://github.com/testorg/testrepo.git", "main", false, "admin"
        );
        scanDataService.upsertManagedRepository(repo);

        TrackedIssueRecord issue = new TrackedIssueRecord(
                repoId + "-42",
                repoId,
                42,
                "Fix Vulnerability in Auth",
                "Details about the vulnerability",
                List.of("auditagent-fix", "security"),
                "OPEN",
                "VULN-AUTH-1",
                "run-auth-1",
                "ANALYZING",
                null,
                null,
                "Remediation initiated",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        scanDataService.saveTrackedIssue(issue);

        Optional<TrackedIssueRecord> found = scanDataService.findTrackedIssue(repoId, 42);
        assertTrue(found.isPresent());
        assertEquals("Fix Vulnerability in Auth", found.get().title());
        assertEquals(repoId, found.get().repositoryId());
        assertEquals(42, found.get().issueNumber());

        List<TrackedIssueRecord> issues = scanDataService.listTrackedIssues(repoId);
        assertEquals(1, issues.size());
        assertEquals("VULN-AUTH-1", issues.get(0).targetVulnerabilityId());

        scanDataService.updateTrackedIssueStatusByPr(101, "FIXED", "CLOSED", "Merged into master");
        // No match for PR 101 yet
        assertEquals("ANALYZING", scanDataService.findTrackedIssue(repoId, 42).get().remediationStatus());
    }
}
