package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record ScanSnapshot(String snapshotId, long repositoryId, String branch, String baseSha,
        String reportKey, String workspacePath, String createdByUserId,
        LocalDateTime createdAt) {
}
