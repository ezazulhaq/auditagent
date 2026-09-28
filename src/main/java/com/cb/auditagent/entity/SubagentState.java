package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subagent_state")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubagentState {
    @Id
    @EqualsAndHashCode.Include
    private String stateId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String parentCheckpointId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String childAgent;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer sequenceNumber;

    @Column(columnDefinition = "TEXT")
    private String contextJson;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String status;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime expiresAt;

}
