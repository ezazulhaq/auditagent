package com.cb.auditagent.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Event log entity stored in PostgreSQL for real-time progress streaming and multi-pod synchronization.
 * Inserts into this table are captured by Supabase Realtime (CDC / WAL) and broadcast to WebSocket clients.
 */
@Entity
@Table(name = "job_event_log", indexes = {
        @Index(name = "idx_job_event_job_id", columnList = "jobId"),
        @Index(name = "idx_job_event_run_id", columnList = "runId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @NotNull
    @Column(nullable = false, length = 64)
    private String jobId;

    @Column(length = 64)
    private String runId;

    @NotNull
    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
