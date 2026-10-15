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
@Table(name = "scan_history_stats", indexes = {
    @Index(name = "idx_scan_history_repo_branch", columnList = "repository_id, branch")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanHistoryStats {
    @Id
    @EqualsAndHashCode.Include
    private String historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ManagedRepository repository;

    @NotNull
    @Column(nullable = false)
    private String branch;

    @NotNull
    @Column(nullable = false)
    private String baseSha;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private Integer totalFindings;

    @NotNull
    @Column(nullable = false)
    private Integer highSeverity;

    @NotNull
    @Column(nullable = false)
    private Integer mediumSeverity;

    @NotNull
    @Column(nullable = false)
    private Integer lowSeverity;

    @NotNull
    @Column(nullable = false)
    private Integer infoSeverity;

    public Long getRepositoryId() {
        return repository != null ? repository.getRepositoryId() : null;
    }

    public void setRepositoryId(Long repositoryId) {
        if (repositoryId != null) {
            this.repository = ManagedRepository.builder().repositoryId(repositoryId).build();
        } else {
            this.repository = null;
        }
    }

}
