package com.cb.auditagent.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.cb.auditagent.domain.ManagedRepository;

public interface SourceControlProvider {
    List<ManagedRepository> listRepositories(String userAccessToken);

    List<String> listBranches(String userAccessToken, ManagedRepository repository);

    String branchHead(ManagedRepository repository, String branch, String installationToken);

    Path workspacePath(String workspaceKey);

    Path cloneAtCommit(ManagedRepository repository, String branch, String expectedSha,
            String installationToken, String workspaceKey);

    boolean userCanPush(String userAccessToken, ManagedRepository repository);

    boolean userCanRead(String userAccessToken, ManagedRepository repository);

    String workspaceDiff(Path workspace);

    List<String> changedFiles(Path workspace);

    Map<String, String> changedFileHashes(Path workspace);

    String workspaceHead(Path workspace);

    void cleanupWorkspace(Path workspace);
}
