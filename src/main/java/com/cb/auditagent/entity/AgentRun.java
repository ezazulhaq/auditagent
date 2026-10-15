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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private MemoryThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vulnerability_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private VulnerabilityEntity vulnerability;

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

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<AgentStep> steps = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<VerificationResult> verificationResults = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<RunChange> changes = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<GraphCheckpoint> checkpoints = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<SubagentState> subagentStates = new java.util.ArrayList<>();

    @OneToOne(mappedBy = "run", fetch = FetchType.LAZY)
    private RunPublication publication;

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

    public String getVulnerabilityId() {
        return vulnerability != null ? vulnerability.getId() : null;
    }

    public void setVulnerabilityId(String vulnerabilityId) {
        if (vulnerabilityId != null) {
            this.vulnerability = VulnerabilityEntity.builder().id(vulnerabilityId).build();
        } else {
            this.vulnerability = null;
        }
    }

}
