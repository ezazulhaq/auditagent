package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "memory_messages",
    uniqueConstraints = { @UniqueConstraint(name = "uq_memory_message_sequence", columnNames = {"thread_id", "sequence_number"}) },
    indexes = { @Index(name = "idx_memory_message_thread", columnList = "thread_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryMessage {
    @Id
    @EqualsAndHashCode.Include
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private MemoryThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private AgentRun run;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long sequenceNumber;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String role;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String messageType;

    @Column(columnDefinition = "TEXT")
    private String contentRedacted;

    private String contentHash;

    private Integer tokenEstimate;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime expiresAt;

    public String getThreadId() {
        return thread != null ? thread.getThreadId() : null;
    }

    public void setThreadId(String threadId) {
        if (threadId != null) {
            this.thread = MemoryThread.builder().threadId(threadId).build();
        } else {
            this.thread = null;
        }
    }

    public String getRunId() {
        return run != null ? run.getRunId() : null;
    }

    public void setRunId(String runId) {
        if (runId != null) {
            this.run = AgentRun.builder().runId(runId).build();
        } else {
            this.run = null;
        }
    }

}
