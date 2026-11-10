package com.cb.auditagent.service.realtime;

import com.cb.auditagent.entity.JobEventLog;
import com.cb.auditagent.repository.JobEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for recording and broadcasting lifecycle events across pods.
 * Writing events to PostgreSQL enables Supabase Realtime to broadcast them via WebSockets
 * to connected clients without relying on node-local in-memory sinks.
 * Also bridges active job events directly to Server-Sent Event (SSE) Flux streams.
 */
@Service
public class EventBroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(EventBroadcastService.class);

    private final JobEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, Sinks.Many<String>> jobSinks = new ConcurrentHashMap<>();
    private final Timer cleanupTimer = new Timer("event-cleanup-timer", true);

    public EventBroadcastService(JobEventLogRepository eventLogRepository, ObjectMapper objectMapper) {
        this.eventLogRepository = eventLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Subscribe to real-time events for a specific job as an SSE Flux stream.
     * Events are formatted as "data: <json>\n\n" lines, including 15-second keepalive pulses.
     * Replays all buffered events if the consumer connects slightly after task start.
     */
    public Flux<String> listenEvents(String jobId) {
        Sinks.Many<String> sink = jobSinks.computeIfAbsent(jobId, k -> Sinks.many().replay().all());

        Timer timer = new Timer("keepalive-" + jobId, true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    sink.tryEmitNext("data: {\"type\":\"keepalive\"}\n\n");
                } catch (Exception ignore) {
                }
            }
        }, 15000, 15000);

        return sink.asFlux().doFinally(signalType -> {
            timer.cancel();
            jobSinks.remove(jobId);
        });
    }

    @Transactional
    public JobEventLog broadcastEvent(String jobId, String runId, String eventType, Map<String, Object> payload) {
        String payloadJson = null;
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                logger.warn("Failed to serialize event payload for job {}: {}", jobId, e.getMessage());
            }
        }

        JobEventLog log = JobEventLog.builder()
                .jobId(jobId != null ? jobId : "unknown")
                .runId(runId)
                .eventType(eventType != null ? eventType : "status")
                .payloadJson(payloadJson)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        JobEventLog saved = eventLogRepository.save(log);
        logger.debug("Broadcasted event [{}] for job [{}] (event id: {})", eventType, jobId, saved.getEventId());

        if (jobId != null) {
            Sinks.Many<String> sink = jobSinks.computeIfAbsent(jobId, k -> Sinks.many().replay().all());
            if (payloadJson != null) {
                sink.tryEmitNext("data: " + payloadJson + "\n\n");
            }
            if (isTerminalEvent(eventType)) {
                sink.tryEmitComplete();
                cleanupTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        jobSinks.remove(jobId);
                    }
                }, 30000);
            }
        }

        return saved;
    }

    public void completeStream(String jobId) {
        if (jobId == null) return;
        Sinks.Many<String> sink = jobSinks.get(jobId);
        if (sink != null) {
            sink.tryEmitComplete();
            cleanupTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    jobSinks.remove(jobId);
                }
            }, 30000);
        }
    }

    private boolean isTerminalEvent(String eventType) {
        return "analysis_complete".equals(eventType)
                || "complete".equals(eventType)
                || "error".equals(eventType);
    }
}
