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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String threadId;

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

}
