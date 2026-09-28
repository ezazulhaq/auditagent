package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "run_publications",
    indexes = { @Index(name = "idx_run_publication_repo_pr", columnList = "repository_id, pr_number") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunPublication {
    @Id
    @EqualsAndHashCode.Include
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String userId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long repositoryId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long installationId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String reportKey;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String baseBranch;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String baseSha;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String workspacePath;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String branchName;

    private String approvalDigest;

    private String approvedBy;

    private java.time.LocalDateTime approvedAt;

    private String commitSha;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String pushStatus;

    private Integer prNumber;

    private String prUrl;

    private String prState;

    @Column(columnDefinition = "TEXT")
    private String publishError;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
