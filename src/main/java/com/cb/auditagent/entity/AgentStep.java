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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private AgentRun run;

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
