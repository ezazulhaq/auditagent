package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record RunPublicationRecord(String runId, String userId, long repositoryId, long installationId,
        String reportKey, String baseBranch, String baseSha, String workspacePath,
        String branchName, String approvalDigest, String approvedBy,
        LocalDateTime approvedAt, String commitSha, String pushStatus,
        Integer pullRequestNumber, String pullRequestUrl,
        String pullRequestState, String publishError) {
}
