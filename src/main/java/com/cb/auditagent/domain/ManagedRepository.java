package com.cb.auditagent.domain;

public record ManagedRepository(long repositoryId, long installationId, String owner, String name,
        String fullName, String cloneUrl, String defaultBranch,
        boolean privateRepository, String permission) {
}
