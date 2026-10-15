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
@Table(name = "verification_results",
    indexes = { @Index(name = "idx_verification_result_run", columnList = "run_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationResult {
    @Id
    @EqualsAndHashCode.Include
    private String verificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @NotNull
    @Column(nullable = false)
    private String kind;

    @NotNull
    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String evidenceRedacted;

    private Integer exitCode;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

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
