package com.cb.auditagent.service.realtime;

import com.cb.auditagent.config.RabbitMqConfig;
import com.cb.auditagent.entity.JobEventLog;
import com.cb.auditagent.repository.JobEventLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for recording and broadcasting lifecycle events across pods.
 * Distributes lifecycle events from any worker pod to web pods via RabbitMQ fanout exchange,
 * and writes events to PostgreSQL job_event_log for persistence and auditing.
 * Also bridges active job events directly to Server-Sent Event (SSE) Flux streams.
 */
@Service
public class EventBroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(EventBroadcastService.class);

    private final JobEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, Sinks.Many<String>> jobSinks = new ConcurrentHashMap<>();
    private final Timer cleanupTimer = new Timer("event-cleanup-timer", true);

    @Value("${auditagent.messaging.events-exchange:" + RabbitMqConfig.EVENTS_EXCHANGE + "}")
    private String eventsExchange = RabbitMqConfig.EVENTS_EXCHANGE;

    public EventBroadcastService(JobEventLogRepository eventLogRepository, ObjectMapper objectMapper) {
        this(eventLogRepository, objectMapper, null);
    }

    @Autowired
    public EventBroadcastService(JobEventLogRepository eventLogRepository, ObjectMapper objectMapper,
            @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.eventLogRepository = eventLogRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
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
                    sink.tryEmitNext("{\"type\":\"keepalive\"}");
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
        Map<String, Object> enrichedPayload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
        if (jobId != null && !enrichedPayload.containsKey("jobId")) {
            enrichedPayload.put("jobId", jobId);
        }
        if (runId != null && !enrichedPayload.containsKey("runId")) {
            enrichedPayload.put("runId", runId);
        }
        if (eventType != null && !enrichedPayload.containsKey("type")) {
            enrichedPayload.put("type", eventType);
        }

        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(enrichedPayload);
        } catch (Exception e) {
            logger.warn("Failed to serialize event payload for job {}: {}", jobId, e.getMessage());
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

        boolean publishedToRabbit = false;
        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(eventsExchange, "", payloadJson != null ? payloadJson : "{}");
                publishedToRabbit = true;
            } catch (Exception e) {
                logger.warn("Failed to publish event to RabbitMQ exchange [{}]: {}", eventsExchange, e.getMessage());
            }
        }

        // If RabbitMQ is not available or publish failed, emit directly to local sink
        if (!publishedToRabbit && jobId != null) {
            emitToLocalSink(jobId, payloadJson, eventType);
        }

        return saved;
    }

    /**
     * Dispatches an event received from RabbitMQ cross-pod fanout exchange to local sink.
     */
    public void onRemoteEvent(String eventJson) {
        if (eventJson == null || eventJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            String jobId = node.has("jobId") ? node.get("jobId").asText() : null;
            if (jobId != null && jobSinks.containsKey(jobId)) {
                String eventType = node.has("type") ? node.get("type").asText() : null;
                emitToLocalSink(jobId, eventJson, eventType);
            }
        } catch (Exception e) {
            logger.debug("Failed to dispatch remote event from RabbitMQ: {}", e.getMessage());
        }
    }

    void emitToLocalSink(String jobId, String payloadJson, String eventType) {
        if (jobId == null) return;
        Sinks.Many<String> sink = jobSinks.computeIfAbsent(jobId, k -> Sinks.many().replay().all());
        if (payloadJson != null) {
            sink.tryEmitNext(payloadJson);
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

    Map<String, Sinks.Many<String>> getJobSinks() {
        return jobSinks;
    }

    private boolean isTerminalEvent(String eventType) {
        return "analysis_complete".equals(eventType)
                || "complete".equals(eventType)
                || "error".equals(eventType);
    }

    /**
     * Ephemeral queue listener on web pods that receives cross-pod events from RabbitMQ.
     */
    @Component
    @ConditionalOnBean(name = "eventIngressQueue")
    public static class EventIngressListener {
        private final EventBroadcastService broadcastService;

        public EventIngressListener(EventBroadcastService broadcastService) {
            this.broadcastService = broadcastService;
        }

        @RabbitListener(
                queues = "#{eventIngressQueue.name}",
                autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
        )
        public void onMessage(String eventJson) {
            broadcastService.onRemoteEvent(eventJson);
        }
    }
}
