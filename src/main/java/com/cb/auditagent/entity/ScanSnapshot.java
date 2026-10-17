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
@Table(name = "scan_snapshots", indexes = {
    @Index(name = "idx_scan_snapshot_repo_branch", columnList = "repository_id, branch, created_at")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSnapshot {
    @Id
    @EqualsAndHashCode.Include
    private String snapshotId;

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
    private String reportKey;

    @NotNull
    @Column(nullable = false)
    private String workspacePath;

    @NotNull
    @Column(nullable = false)
    private String createdByUserId;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

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
