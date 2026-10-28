package com.cb.auditagent.domain;

import java.time.LocalDateTime;
import java.util.List;

public record TrackedIssueRecord(
        String id,
        Long repositoryId,
        Integer issueNumber,
        String title,
        String body,
        List<String> labels,
        String state,
        String targetVulnerabilityId,
        String runId,
        String remediationStatus,
        Integer pullRequestNumber,
        String pullRequestUrl,
        String statusMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
