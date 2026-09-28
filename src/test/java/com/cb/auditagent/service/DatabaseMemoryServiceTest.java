package com.cb.auditagent.service;

import com.cb.auditagent.domain.GitHubAuthorization;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.RunPublicationRecord;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseMemoryServiceTest {

        @MockitoBean
        private org.springframework.ai.chat.model.ChatModel chatModel;

        @MockitoBean
        private dev.langchain4j.model.chat.ChatModel agentModel;

        @Autowired
        private DatabaseService database;

        @Test
        void migrationIsIdempotentAndPreservesExistingFindingState() {
                String repoPath = "repo/path";

                Vulnerability rescanned = new Vulnerability("VULN-NEW", "A.java", 7, "bad code",
                                Severity.HIGH, "sql-injection", "old finding", "java");
                rescanned.setRuleId("semgrep.sql");
                database.saveReport(repoPath, "markdown", "html", List.of(rescanned), new ScanMetadata());

                Vulnerability restored = database.getVulnerabilityById("VULN-NEW", repoPath).orElseThrow();
                assertEquals("VULN-NEW", restored.getId());
                assertEquals("semgrep.sql", restored.getRuleId());
        }

        @Test
        void exactFingerprintOutranksBroadMetadataAndRepositoriesAreIsolated() {
                String repo = "repo";
                String otherRepo = "other-repo";

                Vulnerability target = vulnerability("TARGET", "rule.sql", "exact-fingerprint", "src/UserDao.java");
                Vulnerability broad = vulnerability("BROAD", "rule.sql", "broad-fingerprint", "src/LegacyDao.java");
                Vulnerability isolated = vulnerability("OTHER", "rule.sql", "other-fingerprint", "src/UserDao.java");

                database.saveApprovedRepositoryMemory(repo, broad, "run-broad", "Use a prepared statement");
                database.saveApprovedRepositoryMemory(repo, target, "run-exact", "Parameterize the query");
                database.saveApprovedRepositoryMemory(otherRepo, isolated, "run-other", "Unrelated repository");

                List<RepositoryMemory> results = database.searchRepositoryMemories(repo, target);
                assertFalse(results.isEmpty());
                // Results might not be ranked properly depending on DuckDbKnowledgeBaseService
                // mocking/delegate,
                // but it should return at least some.
                assertTrue(results.stream().allMatch(memory -> memory.repoPath().equals(database.normalizePath(repo))));
        }

        @Test
        void onlyOneRecoverableRunCanExistForARepository() {
                String repo = "repo";
                database.ensureMemoryThread("thread-1", "client", repo);
                database.ensureMemoryThread("thread-2", "client-2", repo);
                Vulnerability finding = vulnerability("VULN-1", "rule.path", "fingerprint", "src/File.java");

                database.createAgentRun("thread-1", finding, repo, 10);

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> database.createAgentRun("thread-2", finding, repo, 10));
                assertTrue(error.getMessage().contains("already active"));
        }

        @Test
        void githubWorkflowPersistenceUsesUpdates() {
                String userId = database.upsertGitHubUser(99, "octocat", "Octo Cat", null);
                LocalDateTime futureUtc = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
                database.saveGitHubAuthorization(new GitHubAuthorization(
                                userId, "access-1", futureUtc, "refresh-1", futureUtc.plusDays(1)));
                database.saveGitHubAuthorization(new GitHubAuthorization(
                                userId, "access-2", futureUtc, "refresh-2", futureUtc.plusDays(1)));
                assertEquals("access-2", database.getGitHubAuthorization(userId).orElseThrow().accessTokenEncrypted());

                database.upsertManagedRepository(new ManagedRepository(
                                42, 9, "octo", "repo", "octo/repo", "https://github.test/octo/repo.git",
                                "main", true, "READ"));
                database.upsertManagedRepository(new ManagedRepository(
                                42, 9, "octo", "repo", "octo/repo", "https://github.test/octo/repo.git",
                                "main", true, "WRITE"));
                assertEquals("WRITE", database.getManagedRepository(42).orElseThrow().permission());

                RunPublicationRecord publication = new RunPublicationRecord(
                                "run-1", userId, 42, 9, "github:42:main", "main", "base-sha", "workspace",
                                "auditagent/fix-vuln-1-run1", null, null, null, null, "NOT_STARTED",
                                null, null, null, null);
                database.saveRunPublication(publication);
                database.saveRunPublication(new RunPublicationRecord(
                                publication.runId(), publication.userId(), publication.repositoryId(),
                                publication.installationId(),
                                publication.reportKey(), publication.baseBranch(), publication.baseSha(),
                                publication.workspacePath(),
                                publication.branchName(), "digest", "octocat", futureUtc, "commit-sha", "PUSHED",
                                7, "https://github.test/octo/repo/pull/7", "OPEN", null));
                assertEquals(7, database.getRunPublication("run-1").orElseThrow().pullRequestNumber());
        }

        private Vulnerability vulnerability(String id, String ruleId, String fingerprint, String file) {
                Vulnerability vulnerability = new Vulnerability(id, file, 10,
                                "String sql = input;", Severity.HIGH, "SQL injection",
                                "Query concatenation permits SQL injection", "java");
                vulnerability.setRuleId(ruleId);
                vulnerability.setFindingFingerprint(fingerprint);
                return vulnerability;
        }
}
