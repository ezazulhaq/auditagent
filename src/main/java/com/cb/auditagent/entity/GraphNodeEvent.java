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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GraphCheckpoint checkpoint;

    @NotNull
    @Column(nullable = false)
    private String nodeName;

    @NotNull
    @Column(nullable = false)
    private String status;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String detailJson;

    @NotNull
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
