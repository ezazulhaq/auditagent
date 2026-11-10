package com.cb.auditagent.service.queue;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.dto.queue.RemediationTaskPayload;
import com.cb.auditagent.dto.queue.ScanTaskPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Task producer for asynchronous job dispatch to RabbitMQ.
 * Decouples client HTTP requests from heavy background scanner processes and LLM loops.
 */
@Service
public class TaskProducerService {
    private static final Logger logger = LoggerFactory.getLogger(TaskProducerService.class);

    private final RabbitTemplate rabbitTemplate;

    public TaskProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Enqueue a repository scan task.
     *
     * @param payload scan metadata and targeting parameters
     * @param priority message priority (1 = normal/bulk, 10 = critical single scan)
     */
    public void enqueueScan(ScanTaskPayload payload, int priority) {
        logger.info("Enqueueing scan task [{}] for repo {} on branch {} (priority {})",
                payload.jobId(), payload.repositoryId(), payload.branch(), priority);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.ROUTING_KEY_SCAN,
                payload,
                message -> {
                    message.getMessageProperties().setPriority(Math.max(1, Math.min(10, priority)));
                    return message;
                }
        );
    }

    /**
     * Enqueue a vulnerability remediation task.
     *
     * @param payload remediation metadata, run ID, and vulnerability identifier
     * @param priority message priority (1 = normal, 10 = critical single vulnerability)
     */
    public void enqueueRemediation(RemediationTaskPayload payload, int priority) {
        logger.info("Enqueueing remediation task [{}] for vuln {} in repo {} (priority {})",
                payload.jobId(), payload.vulnerabilityId(), payload.repositoryId(), priority);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.ROUTING_KEY_REMEDIATION,
                payload,
                message -> {
                    message.getMessageProperties().setPriority(Math.max(1, Math.min(10, priority)));
                    return message;
                }
        );
    }
}
