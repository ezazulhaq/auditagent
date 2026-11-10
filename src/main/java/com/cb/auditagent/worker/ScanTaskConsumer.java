package com.cb.auditagent.worker;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.dto.queue.ScanTaskPayload;
import com.cb.auditagent.service.ScanOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Worker listener that consumes and executes repository scan tasks from RabbitMQ.
 * Controlled by worker prefetch (QoS) to prevent CPU and memory saturation.
 */
@Component
@ConditionalOnProperty(name = "auditagent.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ScanTaskConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ScanTaskConsumer.class);

    private final ScanOrchestratorService scanOrchestrator;

    public ScanTaskConsumer(ScanOrchestratorService scanOrchestrator) {
        this.scanOrchestrator = scanOrchestrator;
    }

    @RabbitListener(queues = RabbitMqConfig.SCAN_QUEUE)
    public void consumeScanTask(ScanTaskPayload task) {
        logger.info("Worker picked up scan task [{}] for repo {} on branch {}",
                task.jobId(), task.repositoryId(), task.branch());
        try {
            scanOrchestrator.executeScanTask(task);
            logger.info("Successfully completed scan task [{}]", task.jobId());
        } catch (Exception e) {
            logger.error("Failed executing scan task [{}]: {}", task.jobId(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Scan task execution failed: " + e.getMessage(), e);
        }
    }
}
