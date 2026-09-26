package com.cb.auditagent.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.PublicationCheckpoint;
import com.cb.auditagent.domain.PublishResult;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.service.GitHubApiClient;
import com.cb.auditagent.service.GitHubProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubProviderIntegrationTest {
        @TempDir
        Path tempDir;

        @Test
        void publishesOneBotCommitAndReusesExistingPullRequest() throws Exception {
                Path remotePath = tempDir.resolve("remote.git");
                try (Git ignored = Git.init().setBare(true).setDirectory(remotePath.toFile()).call()) {
                }
                Path seedPath = tempDir.resolve("seed");
                String baseSha;
                try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedPath.toFile()).call()) {
                        Files.writeString(seedPath.resolve("Example.java"),
                                        "class Example { String unsafe = \"old\"; }\n");
                        seed.add().addFilepattern(".").call();
                        baseSha = seed.commit().setMessage("base").setAuthor("Seeder", "seed@example.test").call()
                                        .name();
                        seed.remoteAdd().setName("origin").setUri(new URIish(remotePath.toUri().toString())).call();
                        seed.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
                }

                GitHubApiClient api = mock(GitHubApiClient.class);
                when(api.findPullRequest(any(), anyString(), anyString(), anyString())).thenReturn(null);
                when(api.createPullRequest(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                                .thenAnswer(invocation -> {
                                        String publishedBranch = invocation.getArgument(2);
                                        try (Repository remote = new FileRepositoryBuilder()
                                                        .setGitDir(remotePath.toFile()).build()) {
                                                return new GitHubApiClient.PrInfo(7,
                                                                "https://github.test/octo/repo/pull/7",
                                                                remote.resolve("refs/heads/" + publishedBranch).name(),
                                                                "open", false);
                                        }
                                });
                GitHubAppConfig config = new GitHubAppConfig();
                config.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
                GitHubProvider provider = new GitHubProvider(api, config);
                ManagedRepository repository = new ManagedRepository(42, 9, "octo", "repo", "octo/repo",
                                remotePath.toUri().toString(), "main", true, "WRITE");
                Path workspace = provider.cloneAtCommit(repository, "main", baseSha, "installation-token", "run-one");
                Files.writeString(workspace.resolve("Example.java"), "class Example { String safe = \"new\"; }\n");
                Files.writeString(workspace.resolve("NewTest.java"), "class NewTest {}\n");
                Path backup = workspace.resolve(".auditagent/backups/Example.java");
                Files.createDirectories(backup.getParent());
                Files.writeString(backup, "must never be published\n");
                assertTrue(provider.changedFiles(workspace).contains("NewTest.java"));
                assertFalse(provider.changedFiles(workspace).stream().anyMatch(path -> path.startsWith(".auditagent")));
                assertTrue(provider.workspaceDiff(workspace).contains("+++ b/NewTest.java"));

                Vulnerability vulnerability = new Vulnerability();
                vulnerability.setId("VULN-000001");
                vulnerability.setVulnType("Injection");
                vulnerability.setSeverity(Severity.HIGH);
                vulnerability.setRuleId("security.injection");
                List<PublicationCheckpoint> checkpoints = new ArrayList<>();
                String branch = "auditagent/fix-vuln-000001-12345678";
                PublishResult published = provider.publish(repository, workspace, "main", branch, "installation-token",
                                "octocat", "12345678-run", vulnerability, "Verified remediation", checkpoints::add);

                assertEquals(7, published.pullRequestNumber());
                assertEquals(List.of("COMMITTED", "PUSHED", "PR_CREATED"),
                                checkpoints.stream().map(PublicationCheckpoint::phase).toList());
                try (Repository remote = new FileRepositoryBuilder().setGitDir(remotePath.toFile()).build()) {
                        ObjectId pushed = remote.resolve("refs/heads/" + branch);
                        assertEquals(published.commitSha(), pushed.name());
                        assertTrue(remote.resolve(pushed.name() + "^{tree}:NewTest.java") != null);
                        assertNull(remote.resolve(pushed.name() + "^{tree}:.auditagent/backups/Example.java"));
                        try (RevWalk walk = new RevWalk(remote)) {
                                RevCommit commit = walk.parseCommit(pushed);
                                assertTrue(commit.getFullMessage().contains("AuditAgent-Run: 12345678-run"));
                                assertTrue(commit.getFullMessage().contains("Approved-by: @octocat"));
                                assertEquals("AuditAgent[bot]", commit.getAuthorIdent().getName());
                        }
                }

                when(api.findPullRequest(any(), anyString(), anyString(), anyString()))
                                .thenReturn(new GitHubApiClient.PrInfo(7, published.pullRequestUrl(),
                                                published.commitSha(), "open", false));
                PublishResult retried = provider.publish(repository, workspace, "main", branch, "installation-token",
                                "octocat", "12345678-run", vulnerability, "Verified remediation", checkpoint -> {
                                });
                assertEquals(published, retried);
                verify(api, times(1)).createPullRequest(any(), anyString(), anyString(), anyString(), anyString(),
                                anyString());
        }

        @Test
        void retriesPrCreationAfterPushWithoutCreatingAnotherCommit() throws Exception {
                Path remotePath = tempDir.resolve("retry-remote.git");
                try (Git ignored = Git.init().setBare(true).setDirectory(remotePath.toFile()).call()) {
                }
                Path seedPath = tempDir.resolve("retry-seed");
                String baseSha;
                try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedPath.toFile()).call()) {
                        Files.writeString(seedPath.resolve("Example.java"),
                                        "class Example { String unsafe = \"old\"; }\n");
                        seed.add().addFilepattern(".").call();
                        baseSha = seed.commit().setMessage("base").setAuthor("Seeder", "seed@example.test").call()
                                        .name();
                        seed.remoteAdd().setName("origin").setUri(new URIish(remotePath.toUri().toString())).call();
                        seed.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
                }

                GitHubApiClient api = mock(GitHubApiClient.class);
                when(api.findPullRequest(any(), anyString(), anyString(), anyString())).thenReturn(null);
                when(api.createPullRequest(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                                .thenThrow(new IllegalStateException("temporary PR API failure"))
                                .thenAnswer(invocation -> {
                                        String publishedBranch = invocation.getArgument(2);
                                        try (Repository remote = new FileRepositoryBuilder()
                                                        .setGitDir(remotePath.toFile()).build()) {
                                                return new GitHubApiClient.PrInfo(8,
                                                                "https://github.test/octo/repo/pull/8",
                                                                remote.resolve("refs/heads/" + publishedBranch).name(),
                                                                "open", false);
                                        }
                                });
                GitHubAppConfig config = new GitHubAppConfig();
                config.setWorkspaceRoot(tempDir.resolve("retry-workspaces").toString());
                GitHubProvider provider = new GitHubProvider(api, config);
                ManagedRepository repository = new ManagedRepository(43, 10, "octo", "repo", "octo/repo",
                                remotePath.toUri().toString(), "main", true, "WRITE");
                Path workspace = provider.cloneAtCommit(repository, "main", baseSha,
                                "installation-token", "retry-run");
                Files.writeString(workspace.resolve("Example.java"), "class Example { String safe = \"new\"; }\n");
                Vulnerability vulnerability = new Vulnerability();
                vulnerability.setId("VULN-000002");
                vulnerability.setVulnType("Injection");
                vulnerability.setSeverity(Severity.HIGH);
                vulnerability.setRuleId("security.injection");
                String branch = "auditagent/fix-vuln-000002-87654321";

                assertThrows(IllegalStateException.class, () -> provider.publish(repository, workspace, "main", branch,
                                "installation-token", "octocat", "87654321-run", vulnerability,
                                "Verified remediation", checkpoint -> {
                                }));
                String pushedAfterFailure;
                try (Repository remote = new FileRepositoryBuilder().setGitDir(remotePath.toFile()).build()) {
                        pushedAfterFailure = remote.resolve("refs/heads/" + branch).name();
                }

                PublishResult retried = provider.publish(repository, workspace, "main", branch,
                                "installation-token", "octocat", "87654321-run", vulnerability,
                                "Verified remediation", checkpoint -> {
                                });

                assertEquals(pushedAfterFailure, retried.commitSha());
                try (Repository remote = new FileRepositoryBuilder().setGitDir(remotePath.toFile()).build();
                                RevWalk walk = new RevWalk(remote)) {
                        RevCommit commit = walk.parseCommit(remote.resolve("refs/heads/" + branch));
                        assertEquals(baseSha, commit.getParent(0).name());
                }
                verify(api, times(2)).createPullRequest(any(), anyString(), anyString(), anyString(), anyString(),
                                anyString());
        }
}
