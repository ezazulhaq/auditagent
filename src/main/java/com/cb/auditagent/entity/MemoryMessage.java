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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String threadId;

    private String runId;

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

}
