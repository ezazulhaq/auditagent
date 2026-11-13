package com.cb.auditagent.service.realtime;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.entity.JobEventLog;
import com.cb.auditagent.repository.JobEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventBroadcastServiceRabbitMqTest {

    private JobEventLogRepository eventLogRepository;
    private ObjectMapper objectMapper;
    private RabbitTemplate rabbitTemplate;
    private EventBroadcastService service;

    @BeforeEach
    void setUp() {
        eventLogRepository = mock(JobEventLogRepository.class);
        objectMapper = new ObjectMapper();
        rabbitTemplate = mock(RabbitTemplate.class);

        when(eventLogRepository.save(any(JobEventLog.class))).thenAnswer(inv -> {
            JobEventLog log = inv.getArgument(0);
            log.setEventId(999L);
            return log;
        });

        service = new EventBroadcastService(eventLogRepository, objectMapper, rabbitTemplate);
    }

    @Test
    void broadcastEventPublishesEnrichedPayloadToRabbitMqExchange() {
        String jobId = "job-cross-pod-1";
        Map<String, Object> payload = Map.of(
                "type", "progress",
                "step", "scanning",
                "progress", 40
        );

        service.broadcastEvent(jobId, "run-1", "progress", payload);

        verify(eventLogRepository).save(any(JobEventLog.class));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EVENTS_EXCHANGE),
                eq(""),
                payloadCaptor.capture()
        );

        String sentJson = payloadCaptor.getValue();
        assertTrue(sentJson.contains("\"jobId\":\"job-cross-pod-1\""));
        assertTrue(sentJson.contains("\"runId\":\"run-1\""));
        assertTrue(sentJson.contains("\"type\":\"progress\""));
        assertTrue(sentJson.contains("\"progress\":40"));
    }

    @Test
    void onRemoteEventDeliversToLocalSinkMatchingJobId() throws Exception {
        String jobId = "job-web-pod-match";
        Flux<String> stream = service.listenEvents(jobId);

        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.subscribe(received::add, err -> latch.countDown(), latch::countDown);

        // Simulate incoming cross-pod message from RabbitMQ
        String eventJson = objectMapper.writeValueAsString(Map.of(
                "jobId", jobId,
                "type", "complete",
                "message", "Remote scan completed"
        ));

        service.onRemoteEvent(eventJson);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Terminal complete event must complete the Flux");
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("Remote scan completed"));
    }

    @Test
    void onRemoteEventDropsUnknownJobIdWithoutError() throws Exception {
        String eventJson = objectMapper.writeValueAsString(Map.of(
                "jobId", "job-other-pod",
                "type", "progress",
                "message", "From unrelated worker"
        ));

        assertDoesNotThrow(() -> service.onRemoteEvent(eventJson));
        assertTrue(service.getJobSinks().isEmpty(), "No sinks should be created for foreign jobId");
    }

    @Test
    void broadcastEventFallsBackToLocalSinkWhenRabbitFails() throws Exception {
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        String jobId = "job-fallback";
        Flux<String> stream = service.listenEvents(jobId);

        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.subscribe(received::add, err -> latch.countDown(), latch::countDown);

        service.broadcastEvent(jobId, "run-1", "complete", Map.of("type", "complete"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("\"type\":\"complete\""));
    }
}
