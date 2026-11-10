package com.cb.auditagent.worker;

import com.cb.auditagent.dto.queue.RemediationTaskPayload;
import com.cb.auditagent.service.RemediationWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemediationTaskConsumerTest {

    private RemediationWorkflowService remediationWorkflow;
    private RemediationTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        remediationWorkflow = mock(RemediationWorkflowService.class);
        consumer = new RemediationTaskConsumer(remediationWorkflow);
    }

    @Test
    void shouldConsumeRemediationTaskSuccessfully() {
        RemediationTaskPayload task = new RemediationTaskPayload(
                "job-1", "user-1", "thread-1", 100L, "main", "VULN-001", null, Instant.now()
        );

        consumer.consumeRemediationTask(task);

        verify(remediationWorkflow).executeRemediationTask(task);
    }

    @Test
    void shouldRejectWithoutRequeueWhenRemediationExecutionFails() {
        RemediationTaskPayload task = new RemediationTaskPayload(
                "job-1", "user-1", "thread-1", 100L, "main", "VULN-001", null, Instant.now()
        );
        doThrow(new RuntimeException("Lifecycle conflict")).when(remediationWorkflow).executeRemediationTask(task);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.consumeRemediationTask(task)
        );

        assertTrue(ex.getMessage().contains("Lifecycle conflict"));
        verify(remediationWorkflow).executeRemediationTask(task);
    }
}
