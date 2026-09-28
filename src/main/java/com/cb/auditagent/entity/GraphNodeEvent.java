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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String checkpointId;

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

}
