package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer totalFindings;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer highSeverity;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer mediumSeverity;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer lowSeverity;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer infoSeverity;

}
