package com.cb.auditagent.controller;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.TrackedIssueRecord;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.SourceControlProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubRepositoryControllerTest {
    private GitHubAuthService auth;
    private SourceControlProvider sourceControl;
    private DatabaseService database;
    private GitHubRepositoryController controller;

    private ManagedRepository sampleRepo;
    private AuthenticatedUser sampleUser;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        auth = mock(GitHubAuthService.class);
        sourceControl = mock(SourceControlProvider.class);
        database = mock(DatabaseService.class);
        controller = new GitHubRepositoryController(auth, sourceControl, database);

        sampleRepo = new ManagedRepository(42L, 999L, "octocat", "repo", "octocat/repo",
                "https://github.com/octocat/repo.git", "main", false, "ADMIN");
        sampleUser = new AuthenticatedUser("user-1", 123L, "octocat", "Octocat", null, "octocat");
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/github/repositories/42/issues").build());
    }

    @Test
    void issuesReturnsTrackedIssuesWhenAuthorized() {
        when(auth.requireUser(exchange)).thenReturn(sampleUser);
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(auth.accessToken(sampleUser.userId())).thenReturn("token-abc");
        when(sourceControl.userCanRead("token-abc", sampleRepo)).thenReturn(true);

        TrackedIssueRecord record = new TrackedIssueRecord(
                "42-18", 42L, 18, "SQL Injection", "body", List.of("security"),
                "OPEN", "VULN-001", "run-1", "PR_OPEN", 19, "https://github.com/octocat/repo/pull/19",
                "PR created", LocalDateTime.now(), LocalDateTime.now()
        );
        when(database.listTrackedIssues(42L)).thenReturn(List.of(record));

        List<TrackedIssueRecord> result = controller.issues(42L, exchange);
        assertEquals(1, result.size());
        assertEquals(18, result.get(0).issueNumber());
        assertEquals("PR_OPEN", result.get(0).remediationStatus());
    }

    @Test
    void issuesThrowsSecurityExceptionWhenUnauthorized() {
        when(auth.requireUser(exchange)).thenReturn(sampleUser);
        when(database.getManagedRepository(42L)).thenReturn(Optional.of(sampleRepo));
        when(auth.accessToken(sampleUser.userId())).thenReturn("token-abc");
        when(sourceControl.userCanRead("token-abc", sampleRepo)).thenReturn(false);

        assertThrows(SecurityException.class, () -> controller.issues(42L, exchange));
    }
}
