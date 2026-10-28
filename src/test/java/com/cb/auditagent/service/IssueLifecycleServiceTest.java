package com.cb.auditagent.service;

import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunRecord;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.ScanSnapshot;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssueLifecycleServiceTest {
    private DatabaseService database;
    private GitHubApiClient github;
    private RemediationWorkflowService remediationWorkflow;
    private LlmService llm;
    private ObjectMapper objectMapper;
    private IssueLifecycleService service;

    private ManagedRepository sampleRepo;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        github = mock(GitHubApiClient.class);
        remediationWorkflow = mock(RemediationWorkflowService.class);
        llm = mock(LlmService.class);
        objectMapper = new ObjectMapper();
        service = new IssueLifecycleService(database, github, remediationWorkflow, llm, objectMapper);

        sampleRepo = new ManagedRepository(42L, 999L, "ezazulhaq", "auditagent", "ezazulhaq/auditagent",
                "https://github.com/ezazulhaq/auditagent.git", "master", false, "ADMIN");
    }

    @Test
    void ignoresUnmanagedRepository() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.empty());

        service.handleIssueEvent(42L, 18, "opened", "Fix issue", "body", List.of("security"));

        verify(database, never()).ensureMemoryThread(anyString(), anyString(), anyString());
        verify(github, never()).createIssueComment(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void ignoresNonTriggerActions() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));

        service.handleIssueEvent(42L, 18, "edited", "Fix issue", "body", List.of("security"));

        verify(database, never()).ensureMemoryThread(anyString(), anyString(), anyString());
    }

    @Test
    void handlesClosedActionAndDiscardsActiveRun() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        AgentRunRecord activeRun = new AgentRunRecord("run-1", "issue-42-18", "VULN-001", "fp-1", "/tmp/ws",
                AgentPhase.AWAITING_APPROVAL, AgentRunStatus.AWAITING_APPROVAL, 1, 0, 10, true, true, true, true,
                "{}", "Summary", null, null, LocalDateTime.now());
        when(database.findRecoverableRuns("issue-42-18")).thenReturn(List.of(activeRun));

        service.handleIssueEvent(42L, 18, "closed", "Fix issue", "body", List.of());

        verify(database).updateAgentRun(eq("run-1"), eq(AgentPhase.DISCARDED), eq(AgentRunStatus.DISCARDED),
                eq(1), eq(0), eq(true), eq(true), eq(true), eq(true), eq("{}"),
                contains("GitHub issue #18 was closed"), any(), any());
    }

    @Test
    void ignoresNonTriggeringLabelsOrContent() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));

        service.handleIssueEvent(42L, 18, "opened", "Update Readme", "Just updating docs", List.of("documentation"));

        verify(database, never()).ensureMemoryThread(anyString(), anyString(), anyString());
    }

    @Test
    void handlesMissingScanReport() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");
        when(database.findLatestScanSnapshot(42L, "master")).thenReturn(Optional.empty());

        service.handleIssueEvent(42L, 18, "opened", "Fix security bug", "Found SQL injection", List.of("security"));

        verify(github, timeout(2000)).createIssueComment(eq(sampleRepo), eq(18),
                contains("no scan report exists for branch `master`"), eq("token-123"));
    }

    @Test
    void handlesNoActiveFindingsInReport() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 42L, "master", "sha-1", "report-1", "/tmp/ws", "user-1",
                LocalDateTime.now());
        when(database.findLatestScanSnapshot(42L, "master")).thenReturn(Optional.of(snapshot));

        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("auditagent");
        Report emptyReport = new Report("report-1", "# Markdown", "<div/>", List.of(), metadata);
        when(database.getReport("report-1")).thenReturn(Optional.of(emptyReport));

        service.handleIssueEvent(42L, 18, "opened", "Fix security bug", "Fix this @auditagent", List.of());

        verify(github, timeout(2000)).createIssueComment(eq(sampleRepo), eq(18),
                contains("no active detected vulnerabilities were found on branch `master`"), eq("token-123"));
    }

    @Test
    void handlesUnmatchedFinding() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 42L, "master", "sha-1", "report-1", "/tmp/ws", "user-1",
                LocalDateTime.now());
        when(database.findLatestScanSnapshot(42L, "master")).thenReturn(Optional.of(snapshot));

        Vulnerability vuln = new Vulnerability("VULN-001", "src/App.java", 10, "SELECT *", Severity.HIGH,
                "SQL Injection", "User query", "java");
        vuln.setStatus(VulnerabilityStatus.DETECTED);
        vuln.setRuleId("rule-1");

        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("auditagent");
        Report report = new Report("report-1", "# Markdown", "<div/>", List.of(vuln), metadata);
        when(database.getReport("report-1")).thenReturn(Optional.of(report));

        when(llm.matchFindingForIssue(anyString(), anyString(), any())).thenReturn(Optional.empty());

        service.handleIssueEvent(42L, 18, "opened", "Unrelated security issue", "No idea @auditagent", List.of());

        verify(github, timeout(2000)).createIssueComment(eq(sampleRepo), eq(18),
                contains("could not identify a matching vulnerability"), eq("token-123"));
    }

    @Test
    void executesRemediationAndPostsPrCommentOnSuccess() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 42L, "master", "sha-1", "report-1", "/tmp/ws", "user-1",
                LocalDateTime.now());
        when(database.findLatestScanSnapshot(42L, "master")).thenReturn(Optional.of(snapshot));

        Vulnerability vuln = new Vulnerability("VULN-001", "src/App.java", 10, "SELECT *", Severity.HIGH,
                "SQL Injection", "User query", "java");
        vuln.setStatus(VulnerabilityStatus.DETECTED);
        vuln.setRuleId("rule-1");

        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("auditagent");
        Report report = new Report("report-1", "# Markdown", "<div/>", List.of(vuln), metadata);
        when(database.getReport("report-1")).thenReturn(Optional.of(report));

        when(llm.matchFindingForIssue(anyString(), anyString(), any())).thenReturn(Optional.of("VULN-001"));

        // Remediation flow mock
        String approvalReadyJson = """
                {"type":"approval_ready","runId":"run-123","preview":{"digest":"test-digest-abc"}}
                """;
        when(remediationWorkflow.analyze(any(AuthenticatedUser.class), eq("issue-42-18"), eq(42L), eq("master"),
                eq("VULN-001"), eq(18))).thenReturn(Flux.just(approvalReadyJson));

        String prCreatedJson = """
                {"type":"pr_created","pullRequestNumber":99,"pullRequestUrl":"https://github.com/ezazulhaq/auditagent/pull/99"}
                """;
        when(remediationWorkflow.decide(any(AuthenticatedUser.class), eq("run-123"), eq("VULN-001"),
                eq("APPROVE_AND_CREATE_PR"), eq("test-digest-abc"), any())).thenReturn(Flux.just(prCreatedJson));

        service.handleIssueEvent(42L, 18, "opened", "Fix VULN-001", "Please remediate VULN-001", List.of("auditagent-fix"));

        verify(database, timeout(2000)).ensureMemoryThread(eq("issue-42-18"), eq("github-bot"), eq("github:42:master"));
        verify(github, timeout(2000)).createIssueComment(eq(sampleRepo), eq(18),
                contains("resolved this issue in pull request #99 (https://github.com/ezazulhaq/auditagent/pull/99)"),
                eq("token-123"));
    }

    @Test
    void postsFailureCommentWhenRemediationFails() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");
        ScanSnapshot snapshot = new ScanSnapshot("snap-1", 42L, "master", "sha-1", "report-1", "/tmp/ws", "user-1",
                LocalDateTime.now());
        when(database.findLatestScanSnapshot(42L, "master")).thenReturn(Optional.of(snapshot));

        Vulnerability vuln = new Vulnerability("VULN-001", "src/App.java", 10, "SELECT *", Severity.HIGH,
                "SQL Injection", "User query", "java");
        vuln.setStatus(VulnerabilityStatus.DETECTED);
        vuln.setRuleId("rule-1");

        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("auditagent");
        Report report = new Report("report-1", "# Markdown", "<div/>", List.of(vuln), metadata);
        when(database.getReport("report-1")).thenReturn(Optional.of(report));

        when(llm.matchFindingForIssue(anyString(), anyString(), any())).thenReturn(Optional.of("VULN-001"));

        // Return empty or failed flux
        when(remediationWorkflow.analyze(any(AuthenticatedUser.class), eq("issue-42-18"), eq(42L), eq("master"),
                eq("VULN-001"), eq(18))).thenReturn(Flux.empty());

        service.handleIssueEvent(42L, 18, "opened", "Fix VULN-001", "Please remediate VULN-001", List.of("auditagent-fix"));

        verify(github, timeout(2000)).createIssueComment(eq(sampleRepo), eq(18),
                contains("automated remediation failed: Verification failed"), eq("token-123"));
    }

    @Test
    void savesTrackedIssueOnTriggerAndUpdatesStatus() {
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(github.installationToken(999L)).thenReturn("token-123");

        service.handleIssueEvent(42L, 18, "opened", "Fix issue", "body with @auditagent", List.of());

        verify(database).saveTrackedIssue(org.mockito.ArgumentMatchers.argThat(record -> 
            record.repositoryId() == 42L && record.issueNumber() == 18 && "OPEN".equals(record.state())
        ));
    }

    @Test
    void syncsIssuesFromGitHubAndPersistsTriggeredOnes() {
        GitHubApiClient.GitHubIssueInfo triggeredIssue = new GitHubApiClient.GitHubIssueInfo(
                25, "SQL Injection in auth", "Please fix @auditagent", "open", List.of("security")
        );
        GitHubApiClient.GitHubIssueInfo untriggeredIssue = new GitHubApiClient.GitHubIssueInfo(
                26, "Update Readme", "Docs only", "open", List.of("documentation")
        );
        when(github.listRepositoryIssues(sampleRepo, "token-123")).thenReturn(List.of(triggeredIssue, untriggeredIssue));
        when(database.findTrackedIssue(42L, 25)).thenReturn(Optional.empty());

        service.syncIssuesFromGitHub(sampleRepo, "token-123");

        verify(database).saveTrackedIssue(org.mockito.ArgumentMatchers.argThat(record -> 
            record.issueNumber() == 25 && "OPEN".equals(record.state()) && "ANALYZING".equals(record.remediationStatus())
        ));
        verify(database, never()).saveTrackedIssue(org.mockito.ArgumentMatchers.argThat(record -> 
            record.issueNumber() == 26
        ));
    }
}
