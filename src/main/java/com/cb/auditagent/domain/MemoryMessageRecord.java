package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record MemoryMessageRecord(
                String messageId,
                String threadId,
                String runId,
                long sequenceNumber,
                String role,
                String messageType,
                String content,
                String metadataJson,
                LocalDateTime createdAt) {
}
