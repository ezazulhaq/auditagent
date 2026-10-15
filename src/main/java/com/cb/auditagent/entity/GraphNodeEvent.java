package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graph_node_events",
    indexes = { @Index(name = "idx_graph_node_event_run", columnList = "run_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphNodeEvent {
    @Id
    @EqualsAndHashCode.Include
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private AgentRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private GraphCheckpoint checkpoint;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String nodeName;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String status;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String detailJson;

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

    public String getCheckpointId() {
        return checkpoint != null ? checkpoint.getCheckpointId() : null;
    }

    public void setCheckpointId(String checkpointId) {
        if (checkpointId != null) {
            this.checkpoint = GraphCheckpoint.builder().checkpointId(checkpointId).build();
        } else {
            this.checkpoint = null;
        }
    }

}
