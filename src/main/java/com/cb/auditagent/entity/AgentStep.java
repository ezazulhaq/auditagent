package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_steps",
    uniqueConstraints = { @UniqueConstraint(name = "uq_agent_step_idempotency", columnNames = {"run_id", "idempotency_key"}) })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStep {
    @Id
    @EqualsAndHashCode.Include
    private String stepId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long sequenceNumber;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer iteration;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String action;

    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String argumentsRedacted;

    @Column(columnDefinition = "TEXT")
    private String resultRedacted;

    private String resultStatus;

    private Long durationMs;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String idempotencyKey;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime expiresAt;

}
