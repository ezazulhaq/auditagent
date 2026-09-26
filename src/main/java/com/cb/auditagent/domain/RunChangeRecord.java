package com.cb.auditagent.domain;

public record RunChangeRecord(
                String changeId,
                String runId,
                String filePath,
                String beforeHash,
                String afterHash,
                String backupPath,
                String state) {
}
