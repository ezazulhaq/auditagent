package com.cb.auditagent.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @JoinColumn(name = "run_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @NotNull
    @Column(name = "thread_id", nullable = false)
    private String threadId;

    private String parentCheckpointId;

    @NotNull
    @Column(nullable = false)
    private String nodeName;

    @Column(columnDefinition = "TEXT")
    private String stateJson;

    private String interruptReason;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime expiresAt;

    @OneToMany(mappedBy = "checkpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GraphNodeEvent> events = new ArrayList<>();

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
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

}
