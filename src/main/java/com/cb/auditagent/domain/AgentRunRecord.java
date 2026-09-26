package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record AgentRunRecord(
                String runId,
                String threadId,
                String vulnerabilityId,
                String findingFingerprint,
                String repoPath,
                AgentPhase phase,
                AgentRunStatus status,
                int iteration,
                int retryCount,
                int maxIterations,
                boolean patchApplied,
                boolean compilePassed,
                boolean rescanPassed,
                boolean testsPassed,
                String checkpointJson,
                String finalSummary,
                String errorCode,
                String errorDetail,
                LocalDateTime updatedAt) {
}
