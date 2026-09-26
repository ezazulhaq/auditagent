package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record RepositoryMemory(
                String memoryId,
                String repoPath,
                String findingFingerprint,
                String ruleId,
                String category,
                String vulnerabilityType,
                String language,
                String framework,
                String filePattern,
                String title,
                String summary,
                String rootCause,
                String remediationPattern,
                String searchText,
                double confidence,
                String sourceRunId,
                int usageCount,
                LocalDateTime updatedAt,
                double score) {
}
