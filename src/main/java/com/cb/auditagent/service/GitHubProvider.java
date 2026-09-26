package com.cb.auditagent.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.PublicationCheckpoint;
import com.cb.auditagent.domain.PublishResult;
import com.cb.auditagent.domain.PullRequestState;
import com.cb.auditagent.domain.Vulnerability;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class GitHubProvider implements SourceControlProvider, PullRequestProvider {
    private final GitHubApiClient api;
    private final GitHubAppConfig config;

    public GitHubProvider(GitHubApiClient api, GitHubAppConfig config) {
        this.api = api;
        this.config = config;
    }

    @Override
    public List<ManagedRepository> listRepositories(String userAccessToken) {
        return api.listRepositories(userAccessToken);
    }

    @Override
    public List<String> listBranches(String userAccessToken, ManagedRepository repository) {
        return api.listBranches(userAccessToken, repository);
    }

    @Override
    public String branchHead(ManagedRepository repository, String branch, String installationToken) {
        return api.branchHead(repository, branch, installationToken);
    }

    @Override
    public boolean userCanPush(String userAccessToken, ManagedRepository repository) {
        return api.userCanPush(userAccessToken, repository);
    }

    @Override
    public boolean userCanRead(String userAccessToken, ManagedRepository repository) {
        return api.userCanRead(userAccessToken, repository);
    }

    @Override
    public Path workspacePath(String workspaceKey) {
        Path root = Path.of(config.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(safeKey(workspaceKey)).normalize();
        if (!workspace.startsWith(root) || workspace.equals(root)) {
            throw new IllegalArgumentException("Invalid workspace key");
        }
        return workspace;
    }

    @Override
    public Path cloneAtCommit(ManagedRepository repository, String branch, String expectedSha,
            String installationToken, String workspaceKey) {
        Path root = Path.of(config.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = workspacePath(workspaceKey);
        try {
            Files.createDirectories(root);
            if (Files.exists(workspace))
                throw new IllegalStateException("Workspace already exists: " + workspaceKey);
            CredentialsProvider credentials = credentials(installationToken);
            try (Git git = Git.cloneRepository().setURI(repository.cloneUrl()).setDirectory(workspace.toFile())
                    .setBranchesToClone(List.of("refs/heads/" + branch)).setBranch("refs/heads/" + branch)
                    .setCloneAllBranches(false).setCredentialsProvider(credentials).call()) {
                String actualSha = git.getRepository().resolve("HEAD").name();
                if (expectedSha != null && !expectedSha.equalsIgnoreCase(actualSha)) {
                    throw new IllegalStateException("STALE_BASE: expected " + expectedSha + " but cloned " + actualSha);
                }
                excludeAgentArtifacts(git);
            }
            return workspace;
        } catch (Exception e) {
            cleanupWorkspace(workspace);
            if (e instanceof IllegalStateException state)
                throw state;
            throw new IllegalStateException("Could not prepare isolated Git workspace", e);
        }
    }

    @Override
    public String workspaceDiff(Path workspace) {
        try (Git git = Git.open(validateWorkspace(workspace).toFile());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            git.diff().setOutputStream(output).call();
            StringBuilder diff = new StringBuilder(output.toString(StandardCharsets.UTF_8));
            Status status = git.status().call();
            for (String file : status.getUntracked().stream().sorted().toList()) {
                Path path = validateChangedPath(workspace, file);
                if (isInternalArtifact(file))
                    continue;
                appendUntrackedDiff(diff, file, path);
            }
            return diff.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate remediation diff", e);
        }
    }

    @Override
    public List<String> changedFiles(Path workspace) {
        try (Git git = Git.open(validateWorkspace(workspace).toFile())) {
            Status status = git.status().call();
            Set<String> files = new LinkedHashSet<>();
            files.addAll(status.getAdded());
            files.addAll(status.getChanged());
            files.addAll(status.getModified());
            files.addAll(status.getRemoved());
            files.addAll(status.getMissing());
            files.addAll(status.getUntracked());
            files.addAll(status.getConflicting());
            return files.stream().filter(file -> !isInternalArtifact(file)).sorted().toList();
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect remediation workspace", e);
        }
    }

    @Override
    public Map<String, String> changedFileHashes(Path workspace) {
        Path root = validateWorkspace(workspace);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String file : changedFiles(root)) {
            try {
                Path resolved = validateChangedPath(root, file);
                hashes.put(file, Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)
                        ? sha256(Files.readAllBytes(resolved))
                        : "DELETED");
            } catch (Exception e) {
                throw new IllegalStateException("Could not hash changed file " + file, e);
            }
        }
        return hashes;
    }

    @Override
    public String workspaceHead(Path workspace) {
        try (Git git = Git.open(validateWorkspace(workspace).toFile())) {
            return git.getRepository().resolve("HEAD").name();
        } catch (Exception e) {
            throw new IllegalStateException("Could not resolve remediation workspace commit", e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public PublishResult publish(ManagedRepository repository, Path workspace, String baseBranch,
            String branchName, String installationToken, String approvingLogin,
            String runId, Vulnerability vulnerability, String summary,
            Consumer<PublicationCheckpoint> checkpoint) {
        try (Git git = Git.open(validateWorkspace(workspace).toFile())) {
            Ref localBranch = git.getRepository().findRef("refs/heads/" + branchName);
            if (localBranch == null)
                git.checkout().setCreateBranch(true).setName(branchName).call();
            else
                git.checkout().setName(branchName).call();

            Status status = git.status().call();
            String expectedTrailer = "AuditAgent-Run: " + runId;
            String headMessage = git.log().setMaxCount(1).call().iterator().next().getFullMessage();
            boolean approvedCommitExists = headMessage.contains(expectedTrailer);
            String commitSha;
            if (!status.isClean()) {
                if (approvedCommitExists) {
                    throw new IllegalStateException("Workspace changed after the approved commit was created");
                }
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                String title = "fix(security): remediate " + vulnerability.getId();
                String message = title + "\n\n" + expectedTrailer + "\nApproved-by: @" + approvingLogin;
                PersonIdent bot = new PersonIdent("AuditAgent[bot]", "auditagent[bot]@users.noreply.github.com",
                        java.util.Date.from(Instant.now()), java.util.TimeZone.getDefault());
                commitSha = git.commit().setMessage(message).setAuthor(bot).setCommitter(bot).call().name();
            } else {
                if (!approvedCommitExists) {
                    throw new IllegalStateException("No approved remediation changes are available to publish");
                }
                commitSha = git.getRepository().resolve("HEAD").name();
            }
            checkpoint.accept(new PublicationCheckpoint("COMMITTED", commitSha));

            GitHubApiClient.PrInfo existing = api.findPullRequest(repository, baseBranch, branchName,
                    installationToken);
            if (existing != null) {
                if (!commitSha.equalsIgnoreCase(existing.headSha())) {
                    throw new IllegalStateException("Existing pull request head does not match the approved commit");
                }
                checkpoint.accept(new PublicationCheckpoint("PUSHED", commitSha));
                checkpoint.accept(new PublicationCheckpoint("PR_CREATED", commitSha));
                return new PublishResult(branchName, commitSha, existing.number(), existing.url());
            }

            Iterable<PushResult> results = git.push().setCredentialsProvider(credentials(installationToken))
                    .setRemote("origin").add("refs/heads/" + branchName).call();
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() != RemoteRefUpdate.Status.OK
                            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw new IllegalStateException("GitHub rejected branch push: " + update.getStatus());
                    }
                }
            }
            checkpoint.accept(new PublicationCheckpoint("PUSHED", commitSha));

            String prTitle = "[AuditAgent] Fix " + vulnerability.getVulnType() + " (" + vulnerability.getId() + ")";
            String prBody = "## Security remediation\n\n" + summary + "\n\n" +
                    "- Finding: `" + vulnerability.getId() + "`\n" +
                    "- Rule: `" + String.valueOf(vulnerability.getRuleId()) + "`\n" +
                    "- Severity: `" + String.valueOf(vulnerability.getSeverity()) + "`\n" +
                    "- Approved by: @" + approvingLogin + "\n" +
                    "- AuditAgent run: `" + runId + "`\n\n" +
                    "This pull request was verified locally by AuditAgent and still requires normal repository review and CI.";
            GitHubApiClient.PrInfo pr = api.createPullRequest(repository, baseBranch, branchName,
                    prTitle, prBody, installationToken);
            if (!commitSha.equalsIgnoreCase(pr.headSha())) {
                throw new IllegalStateException("Created pull request head does not match the approved commit");
            }
            checkpoint.accept(new PublicationCheckpoint("PR_CREATED", commitSha));
            return new PublishResult(branchName, commitSha, pr.number(), pr.url());
        } catch (Exception e) {
            if (e instanceof IllegalStateException state)
                throw state;
            throw new IllegalStateException("Could not publish remediation", e);
        }
    }

    @Override
    public PublishResult findExisting(ManagedRepository repository, String baseBranch, String branchName,
            String installationToken) {
        GitHubApiClient.PrInfo existing = api.findPullRequest(repository, baseBranch, branchName, installationToken);
        return existing == null ? null
                : new PublishResult(branchName, existing.headSha(), existing.number(), existing.url());
    }

    @Override
    public PullRequestState getState(ManagedRepository repository, int pullRequestNumber, String installationToken) {
        GitHubApiClient.PrInfo pr = api.getPullRequest(repository, pullRequestNumber, installationToken);
        return new PullRequestState(pr.number(), pr.url(), pr.headSha(), pr.state(), pr.merged());
    }

    @Override
    public void cleanupWorkspace(Path workspace) {
        if (workspace == null)
            return;
        Path root = Path.of(config.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path target = workspace.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root) || !Files.exists(target))
            return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (Exception e) {
            throw new IllegalStateException("Could not clean managed workspace", e);
        }
    }

    private Path validateWorkspace(Path workspace) {
        Path root = Path.of(config.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path target = workspace.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root) || !Files.isDirectory(target)) {
            throw new IllegalArgumentException("Workspace is not managed by AuditAgent");
        }
        return target;
    }

    private CredentialsProvider credentials(String token) {
        return new UsernamePasswordCredentialsProvider("x-access-token", token.toCharArray());
    }

    private void excludeAgentArtifacts(Git git) throws Exception {
        Path exclude = git.getRepository().getDirectory().toPath().resolve("info").resolve("exclude");
        Files.createDirectories(exclude.getParent());
        String existing = Files.exists(exclude) ? Files.readString(exclude, StandardCharsets.UTF_8) : "";
        String additions = "\n# AuditAgent managed-workspace artifacts\n.auditagent/\ntarget/\nbuild/\nnode_modules/\n";
        if (!existing.contains("# AuditAgent managed-workspace artifacts")) {
            Files.writeString(exclude, existing + additions, StandardCharsets.UTF_8);
        }
    }

    private void appendUntrackedDiff(StringBuilder diff, String file, Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Unsupported untracked path in remediation: " + file);
        }
        byte[] bytes = Files.readAllBytes(path);
        String normalized = file.replace('\\', '/');
        diff.append("\ndiff --git a/").append(normalized).append(" b/").append(normalized).append('\n')
                .append("new file mode 100644\n--- /dev/null\n+++ b/").append(normalized).append('\n');
        for (byte value : bytes) {
            if (value == 0) {
                diff.append("Binary file ").append(normalized).append(" added\n");
                return;
            }
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = content.lines().toList();
        diff.append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
        for (String line : lines)
            diff.append('+').append(line).append('\n');
    }

    private Path validateChangedPath(Path workspace, String file) throws Exception {
        Path root = validateWorkspace(workspace).toRealPath();
        Path resolved = root.resolve(file).normalize();
        if (!resolved.startsWith(root))
            throw new IllegalStateException("Changed path escaped workspace");
        Path current = root;
        for (Path component : root.relativize(resolved)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalStateException("Symbolic-link changes are not permitted: " + file);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toRealPath().startsWith(root)) {
                throw new IllegalStateException("Changed path escaped workspace: " + file);
            }
        }
        return resolved;
    }

    private boolean isInternalArtifact(String file) {
        String normalized = file.replace('\\', '/');
        return normalized.equals(".auditagent") || normalized.startsWith(".auditagent/");
    }

    private String safeKey(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
