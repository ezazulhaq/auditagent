package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graph_checkpoints",
    indexes = { @Index(name = "idx_graph_checkpoint_run", columnList = "run_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphCheckpoint {
    @Id
    @EqualsAndHashCode.Include
    private String checkpointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private AgentRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private MemoryThread thread;

    private String parentCheckpointId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String nodeName;

    @Column(columnDefinition = "TEXT")
    private String stateJson;

    private String interruptReason;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime expiresAt;

    @OneToMany(mappedBy = "checkpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<GraphNodeEvent> events = new java.util.ArrayList<>();

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

}
