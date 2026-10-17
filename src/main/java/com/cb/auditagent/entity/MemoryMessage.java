package com.cb.auditagent.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MemoryThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private AgentRun run;

    @NotNull
    @Column(nullable = false)
    private Long sequenceNumber;

    @NotNull
    @Column(nullable = false)
    private String role;

    @NotNull
    @Column(nullable = false)
    private String messageType;

    @Column(columnDefinition = "TEXT")
    private String contentRedacted;

    private String contentHash;

    private Integer tokenEstimate;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @NotNull
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
