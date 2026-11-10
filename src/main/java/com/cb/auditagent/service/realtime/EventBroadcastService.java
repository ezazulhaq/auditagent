package com.cb.auditagent.service.realtime;

import com.cb.auditagent.entity.JobEventLog;
import com.cb.auditagent.repository.JobEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Service for recording and broadcasting lifecycle events across pods.
 * Writing events to PostgreSQL enables Supabase Realtime to broadcast them via WebSockets
 * to connected clients without relying on node-local in-memory sinks.
 */
@Service
public class EventBroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(EventBroadcastService.class);

    private final JobEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    public EventBroadcastService(JobEventLogRepository eventLogRepository, ObjectMapper objectMapper) {
        this.eventLogRepository = eventLogRepository;
        this.objectMapper = objectMapper;
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
        return saved;
    }
}
