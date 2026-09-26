package com.cb.auditagent.service;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.domain.PublicationCheckpoint;
import com.cb.auditagent.domain.PublishResult;
import com.cb.auditagent.domain.PullRequestState;
import com.cb.auditagent.domain.Vulnerability;

public interface PullRequestProvider {
    PublishResult publish(ManagedRepository repository, Path workspace, String baseBranch,
            String branchName, String installationToken, String approvingLogin,
            String runId, Vulnerability vulnerability, String summary,
            Consumer<PublicationCheckpoint> checkpoint);

    PublishResult findExisting(ManagedRepository repository, String baseBranch, String branchName,
            String installationToken);

    PullRequestState getState(ManagedRepository repository, int pullRequestNumber, String installationToken);
}
