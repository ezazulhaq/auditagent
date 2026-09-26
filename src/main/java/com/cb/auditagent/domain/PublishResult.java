package com.cb.auditagent.domain;

public record PublishResult(String branchName, String commitSha,
        int pullRequestNumber, String pullRequestUrl) {
}
