package com.cb.auditagent.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @NotNull
    @Column(nullable = false)
    private String parentCheckpointId;

    @NotNull
    @Column(nullable = false)
    private String childAgent;

    @NotNull
    @Column(nullable = false)
    private Integer sequenceNumber;

    @Column(columnDefinition = "TEXT")
    private String contextJson;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @NotNull
    @Column(nullable = false)
    private String status;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

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
