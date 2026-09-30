package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long repositoryId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String branch;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String baseSha;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String reportKey;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String workspacePath;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String createdByUserId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
