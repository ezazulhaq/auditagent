package com.cb.auditagent.worker;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.dto.queue.RemediationTaskPayload;
import com.cb.auditagent.service.RemediationWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Worker listener that consumes and executes vulnerability remediation tasks from RabbitMQ.
 * Controlled by worker prefetch (QoS = 1) to prevent LLM rate-limit saturation.
 */
@Component
@ConditionalOnProperty(name = "auditagent.worker.enabled", havingValue = "true", matchIfMissing = true)
public class RemediationTaskConsumer {
    private static final Logger logger = LoggerFactory.getLogger(RemediationTaskConsumer.class);

    private final RemediationWorkflowService remediationWorkflow;

    public RemediationTaskConsumer(RemediationWorkflowService remediationWorkflow) {
        this.remediationWorkflow = remediationWorkflow;
    }

    @RabbitListener(
            queues = RabbitMqConfig.REMEDIATION_QUEUE,
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void consumeRemediationTask(RemediationTaskPayload task) {
        logger.info("Worker picked up remediation task [{}] for vuln {} in repo {}",
                task.jobId(), task.vulnerabilityId(), task.repositoryId());
        try {
            remediationWorkflow.executeRemediationTask(task);
            logger.info("Successfully completed remediation task [{}]", task.jobId());
        } catch (Exception e) {
            logger.error("Failed executing remediation task [{}]: {}", task.jobId(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Remediation task execution failed: " + e.getMessage(), e);
        }
    }
}
