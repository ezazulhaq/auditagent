package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.GitHubAuthorization;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.RunPublicationRecord;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMemoryServiceTest {
        @TempDir
        Path tempDir;

        private DatabaseService database(Path path) {
                MemoryConfig config = new MemoryConfig();
                config.setFtsEnabled(false);
                DatabaseService database = new DatabaseService(path.toString(), new ObjectMapper(), config);
                database.init();
                return database;
        }

        @Test
        void migrationIsIdempotentAndPreservesExistingFindingState() throws Exception {
                Path path = tempDir.resolve("legacy.duckdb");
                String repoPath = tempDir.resolve("legacy-repo").toFile().getCanonicalPath();
                Class.forName("org.duckdb.DuckDBDriver");
                try (var connection = DriverManager.getConnection("jdbc:duckdb:" + path);
                                var statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE reports (repo_path VARCHAR PRIMARY KEY, md_report VARCHAR, " +
                                        "html_report VARCHAR, findings VARCHAR, metadata VARCHAR)");
                        statement.execute(
                                        "CREATE TABLE vulnerabilities (id VARCHAR PRIMARY KEY, repo_path VARCHAR, file VARCHAR, "
                                                        +
                                                        "type VARCHAR, status VARCHAR, description VARCHAR, code_context VARCHAR, severity VARCHAR, "
                                                        +
                                                        "line_number INTEGER, proposed_fix VARCHAR)");
                        try (var insert = connection.prepareStatement(
                                        "INSERT INTO vulnerabilities VALUES ('VULN-OLD',?,'A.java','sql-injection'," +
                                                        "'FIXED','old finding','bad code','HIGH',7,'fixed code')")) {
                                insert.setString(1, repoPath);
                                insert.executeUpdate();
                        }
                }

                DatabaseService first = database(path);
                DatabaseService second = database(path);
                Vulnerability restored = second.getVulnerabilityById("VULN-OLD", repoPath).orElseThrow();
                Vulnerability rescanned = new Vulnerability("VULN-NEW", "A.java", 7, "bad code",
                                Severity.HIGH, "sql-injection", "old finding", "java");
                rescanned.setRuleId("semgrep.sql");
                second.saveReport(repoPath, "markdown", "html", List.of(rescanned), new ScanMetadata());

                assertEquals(VulnerabilityStatus.FIXED, restored.getStatus());
                assertEquals("fixed code", restored.getProposedFix());
                assertEquals("VULN-OLD", rescanned.getId());
                assertEquals(VulnerabilityStatus.FIXED, rescanned.getStatus());
                assertNotNull(rescanned.getFindingFingerprint());
                assertTrue(path.resolveSibling("legacy.duckdb.pre-memory-v1.bak").toFile().isFile());
                try (var connection = DriverManager.getConnection("jdbc:duckdb:" + path);
                                var statement = connection.createStatement();
                                var result = statement.executeQuery(
                                                "SELECT COUNT(*) FROM schema_version WHERE version IN (1, 2, 3)")) {
                        assertTrue(result.next());
                        assertEquals(3, result.getInt(1));
                }
                assertNotNull(first);
        }

        @Test
        void exactFingerprintOutranksBroadMetadataAndRepositoriesAreIsolated() {
                Path repo = tempDir.resolve("repo");
                Path otherRepo = tempDir.resolve("other-repo");
                DatabaseService database = database(tempDir.resolve("retrieval.duckdb"));

                Vulnerability target = vulnerability("TARGET", "rule.sql", "exact-fingerprint", "src/UserDao.java");
                Vulnerability broad = vulnerability("BROAD", "rule.sql", "broad-fingerprint", "src/LegacyDao.java");
                Vulnerability isolated = vulnerability("OTHER", "rule.sql", "other-fingerprint", "src/UserDao.java");

                database.saveApprovedRepositoryMemory(repo.toString(), broad, "run-broad", "Use a prepared statement");
                database.saveApprovedRepositoryMemory(repo.toString(), target, "run-exact", "Parameterize the query");
                database.saveApprovedRepositoryMemory(otherRepo.toString(), isolated, "run-other",
                                "Unrelated repository");

                List<RepositoryMemory> results = database.searchRepositoryMemories(repo.toString(), target);

                assertFalse(results.isEmpty());
                assertEquals("exact-fingerprint", results.get(0).findingFingerprint());
                assertTrue(results.stream()
                                .allMatch(memory -> memory.repoPath().equals(database.normalizePath(repo.toString()))));
        }

        @Test
        void onlyOneRecoverableRunCanExistForARepository() {
                Path repo = tempDir.resolve("repo");
                DatabaseService database = database(tempDir.resolve("runs.duckdb"));
                database.ensureMemoryThread("thread-1", "client", repo.toString());
                database.ensureMemoryThread("thread-2", "client-2", repo.toString());
                Vulnerability finding = vulnerability("VULN-1", "rule.path", "fingerprint", "src/File.java");

                database.createAgentRun("thread-1", finding, repo.toString(), 10);

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> database.createAgentRun("thread-2", finding, repo.toString(), 10));
                assertTrue(error.getMessage().contains("already active"));
        }

        @Test
        void authenticationExpiryUsesUtcWhenDuckDbRunsInAnotherTimeZone() throws Exception {
                Path path = tempDir.resolve("authentication-timezone.duckdb");
                DatabaseService database = database(path);
                String userId = database.upsertGitHubUser(99, "octocat", "Octo Cat", null);
                LocalDateTime futureUtc = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
                database.saveOAuthState("state-hash", "encrypted-verifier", futureUtc);
                database.createUserSession("session-hash", userId, "csrf-token", futureUtc);

                try (var connection = DriverManager.getConnection("jdbc:duckdb:" + path);
                                var statement = connection.createStatement()) {
                        statement.execute("SET GLOBAL TimeZone='Pacific/Auckland'");

                        database.deleteExpiredMemory();

                        assertEquals("encrypted-verifier", database.consumeOAuthState("state-hash").orElseThrow());
                        assertEquals("octocat", database.findAuthenticatedUser("session-hash").orElseThrow().login());
                }
        }

        @Test
        void githubWorkflowPersistenceUsesDuckDbCompatibleUpdates() {
                DatabaseService database = database(tempDir.resolve("github-upserts.duckdb"));
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

        @Test
        void duckDbFtsIndexCanBeBuiltWhenExtensionIsAvailable() {
                MemoryConfig config = new MemoryConfig();
                config.setFtsEnabled(false);
                DatabaseService database = new DatabaseService(
                                tempDir.resolve("fts.duckdb").toString(), new ObjectMapper(), config);
                database.init();
                Path repo = tempDir.resolve("fts-repo");
                Vulnerability finding = vulnerability(
                                "FTS", "security.sql-injection", "fts-fingerprint", "src/AccountDao.java");
                database.saveApprovedRepositoryMemory(
                                repo.toString(), finding, "fts-run", "Replace concatenation with a prepared statement");

                config.setFtsEnabled(true);
                database.ensureFtsIndex();
                org.junit.jupiter.api.Assumptions.assumeTrue(
                                database.isFtsAvailable(), "DuckDB FTS extension is unavailable in this environment");

                assertFalse(database.searchRepositoryMemories(repo.toString(), finding).isEmpty());
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
