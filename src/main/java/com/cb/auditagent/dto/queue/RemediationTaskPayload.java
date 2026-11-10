package com.cb.auditagent.dto.queue;

import java.io.Serializable;
import java.time.Instant;

/**
 * Task message payload for asynchronous vulnerability remediation via RabbitMQ.
 */
public record RemediationTaskPayload(
        String jobId,
        String userId,
        String threadId,
        long repositoryId,
        String branch,
        String vulnerabilityId,
        Integer issueNumber,
        Instant createdAt
) implements Serializable {}
