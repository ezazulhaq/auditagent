package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_runs",
    indexes = { @Index(name = "idx_agent_runs_thread_status", columnList = "thread_id, status") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRun {
    @Id
    @EqualsAndHashCode.Include
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String threadId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String vulnerabilityId;

    private String findingFingerprint;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String repoPath;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String phase;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String status;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer iteration;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer retryCount;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer maxIterations;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean patchApplied;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean compilePassed;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean rescanPassed;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean testsPassed;

    @Column(columnDefinition = "TEXT")
    private String checkpointJson;

    @Column(columnDefinition = "TEXT")
    private String finalSummary;

    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorDetail;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    private java.time.LocalDateTime completedAt;

}
