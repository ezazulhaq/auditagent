package com.cb.auditagent.worker;

import com.cb.auditagent.dto.queue.ScanTaskPayload;
import com.cb.auditagent.service.ScanOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanTaskConsumerTest {

    private ScanOrchestratorService scanOrchestrator;
    private ScanTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        scanOrchestrator = mock(ScanOrchestratorService.class);
        consumer = new ScanTaskConsumer(scanOrchestrator);
    }

    @Test
    void shouldConsumeScanTaskSuccessfully() {
        ScanTaskPayload task = new ScanTaskPayload(
                "job-1", "user-1", "thread-1", 100L, "main", "sha-1", "semgrep", false, Instant.now()
        );

        consumer.consumeScanTask(task);

        verify(scanOrchestrator).executeScanTask(task);
    }

    @Test
    void shouldRejectWithoutRequeueWhenScanExecutionFails() {
        ScanTaskPayload task = new ScanTaskPayload(
                "job-1", "user-1", "thread-1", 100L, "main", "sha-1", "semgrep", false, Instant.now()
        );
        doThrow(new RuntimeException("Clone failed")).when(scanOrchestrator).executeScanTask(task);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.consumeScanTask(task)
        );

        assertTrue(ex.getMessage().contains("Clone failed"));
        verify(scanOrchestrator).executeScanTask(task);
    }
}
