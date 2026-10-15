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
@Table(name = "run_changes", indexes = {
    @Index(name = "idx_run_change_run", columnList = "run_id")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunChange {
    @Id
    @EqualsAndHashCode.Include
    private String changeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @NotNull
    @Column(length = 1024, nullable = false)
    private String filePath;

    private String beforeHash;

    private String afterHash;

    @Column(length = 1024)
    private String backupPath;

    @NotNull
    @Column(nullable = false)
    private String state;

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
