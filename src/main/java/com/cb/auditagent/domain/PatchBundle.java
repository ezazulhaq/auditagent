package com.cb.auditagent.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates a stateless patch bundle for reconstructing and publishing
 * verified code remediations across distributed nodes without relying on
 * local filesystem workspace persistence.
 */
public record PatchBundle(
        String runId,
        String vulnerabilityId,
        long repositoryId,
        String baseBranch,
        String baseSha,
        String branchName,
        String unifiedDiff,
        List<String> changedFiles,
        Map<String, String> fileHashes,
        Map<String, String> fileContents,
        String summary,
        String createdAt) {
}
