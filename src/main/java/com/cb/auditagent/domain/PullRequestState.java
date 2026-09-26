package com.cb.auditagent.domain;

public record PullRequestState(int number, String url, String commitSha, String state, boolean merged) {
}
