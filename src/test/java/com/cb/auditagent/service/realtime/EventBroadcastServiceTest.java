package com.cb.auditagent.service.realtime;

import com.cb.auditagent.entity.JobEventLog;
import com.cb.auditagent.repository.JobEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventBroadcastServiceTest {

    private JobEventLogRepository eventLogRepository;
    private ObjectMapper objectMapper;
    private EventBroadcastService eventBroadcastService;

    @BeforeEach
    void setUp() {
        eventLogRepository = mock(JobEventLogRepository.class);
        objectMapper = new ObjectMapper();
        when(eventLogRepository.save(any(JobEventLog.class))).thenAnswer(invocation -> {
            JobEventLog log = invocation.getArgument(0);
            log.setEventId(101L);
            return log;
        });
        eventBroadcastService = new EventBroadcastService(eventLogRepository, objectMapper);
    }

    @Test
    void shouldPersistEventAndStreamToSseSubscriber() throws Exception {
        String jobId = "job-stream-1";
        Flux<String> stream = eventBroadcastService.listenEvents(jobId);

        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.subscribe(received::add, error -> latch.countDown(), latch::countDown);

        eventBroadcastService.broadcastEvent(jobId, "run-1", "workspace_prepared", Map.of(
                "type", "workspace_prepared",
                "runId", "run-1"
        ));
        eventBroadcastService.broadcastEvent(jobId, "run-1", "approval_ready", Map.of(
                "type", "approval_ready",
                "runId", "run-1"
        ));
        eventBroadcastService.broadcastEvent(jobId, "run-1", "analysis_complete", Map.of(
                "type", "analysis_complete",
                "runId", "run-1"
        ));

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Stream should complete after terminal event");
        assertEquals(3, received.size());
        assertTrue(received.get(0).contains("\"type\":\"workspace_prepared\""));
        assertTrue(received.get(1).contains("\"type\":\"approval_ready\""));
        assertTrue(received.get(2).contains("\"type\":\"analysis_complete\""));

        ArgumentCaptor<JobEventLog> captor = ArgumentCaptor.forClass(JobEventLog.class);
        verify(eventLogRepository, times(3)).save(captor.capture());
        assertEquals("workspace_prepared", captor.getAllValues().get(0).getEventType());
        assertEquals("approval_ready", captor.getAllValues().get(1).getEventType());
        assertEquals("analysis_complete", captor.getAllValues().get(2).getEventType());
    }

    @Test
    void shouldReplayEventsForLateSubscribers() {
        String jobId = "job-stream-late";

        eventBroadcastService.broadcastEvent(jobId, null, "queued", Map.of(
                "type", "queued",
                "message", "Task queued in RabbitMQ"
        ));
        eventBroadcastService.broadcastEvent(jobId, "run-2", "analysis_complete", Map.of(
                "type", "analysis_complete"
        ));

        Flux<String> stream = eventBroadcastService.listenEvents(jobId);
        List<String> events = stream.collectList().block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertEquals(2, events.size());
        assertTrue(events.get(0).contains("\"type\":\"queued\""));
        assertTrue(events.get(1).contains("\"type\":\"analysis_complete\""));
    }

    @Test
    void shouldTerminateStreamOnErrorEvent() throws Exception {
        String jobId = "job-stream-err";
        Flux<String> stream = eventBroadcastService.listenEvents(jobId);

        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.subscribe(received::add, error -> latch.countDown(), latch::countDown);

        eventBroadcastService.broadcastEvent(jobId, null, "error", Map.of(
                "type", "error",
                "message", "Rate limit exceeded"
        ));

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Stream should complete on error terminal event");
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("\"type\":\"error\""));
        assertTrue(received.get(0).contains("Rate limit exceeded"));
    }

    @Test
    void shouldCompleteStreamExplicitly() throws Exception {
        String jobId = "job-stream-explicit";
        Flux<String> stream = eventBroadcastService.listenEvents(jobId);

        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.subscribe(received::add, error -> latch.countDown(), latch::countDown);

        eventBroadcastService.broadcastEvent(jobId, null, "graph_step", Map.of(
                "type", "graph_step",
                "node", "step1"
        ));
        eventBroadcastService.completeStream(jobId);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Stream should complete explicitly");
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("\"type\":\"graph_step\""));
    }
}
