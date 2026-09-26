package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record ScanHistoryRecord(
        String historyId,
        long repositoryId,
        String branch,
        String baseSha,
        LocalDateTime createdAt,
        int totalFindings,
        int highSeverity,
        int mediumSeverity,
        int lowSeverity,
        int infoSeverity) {
}
