package com.cb.auditagent.service.queue;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.dto.queue.RemediationTaskPayload;
import com.cb.auditagent.dto.queue.ScanTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitMqTaskQueueTest {

    private RabbitTemplate rabbitTemplate;
    private TaskProducerService taskProducerService;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        taskProducerService = new TaskProducerService(rabbitTemplate);
    }

    @Test
    void shouldEnqueueScanTaskWithPriority() {
        ScanTaskPayload payload = new ScanTaskPayload(
                "job-1", "user-1", "thread-1", 100L, "main", "sha-1", "semgrep", false, Instant.now()
        );

        taskProducerService.enqueueScan(payload, 5);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY_SCAN),
                eq(payload),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void shouldEnqueueRemediationTaskWithPriority() {
        RemediationTaskPayload payload = new RemediationTaskPayload(
                "job-2", "user-1", "thread-1", 100L, "main", "VULN-001", null, Instant.now()
        );

        taskProducerService.enqueueRemediation(payload, 10);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY_REMEDIATION),
                eq(payload),
                any(MessagePostProcessor.class)
        );
    }
}
