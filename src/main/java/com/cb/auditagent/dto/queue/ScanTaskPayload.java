package com.cb.auditagent.dto.queue;

import java.io.Serializable;
import java.time.Instant;

/**
 * Task message payload for asynchronous repository scanning via RabbitMQ.
 */
public record ScanTaskPayload(
        String jobId,
        String userId,
        String threadId,
        long repositoryId,
        String branch,
        String baseSha,
        String scannerName,
        boolean forceRescan,
        Instant createdAt
) implements Serializable {}
