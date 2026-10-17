package com.cb.auditagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AgentRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ManagedRepository repository;

    @NotNull
    @Column(nullable = false)
    private Long installationId;

    @NotNull
    @Column(nullable = false)
    private String reportKey;

    @NotNull
    @Column(nullable = false)
    private String baseBranch;

    @NotNull
    @Column(nullable = false)
    private String baseSha;

    @NotNull
    @Column(nullable = false)
    private String workspacePath;

    @NotNull
    @Column(nullable = false)
    private String branchName;

    private String approvalDigest;

    private String approvedBy;

    private java.time.LocalDateTime approvedAt;

    private String commitSha;

    @NotNull
    @Column(nullable = false)
    private String pushStatus;

    private Integer prNumber;

    private String prUrl;

    private String prState;

    @Column(columnDefinition = "TEXT")
    private String publishError;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getUserId() {
        return user != null ? user.getUserId() : null;
    }

    public void setUserId(String userId) {
        if (userId != null) {
            this.user = AppUser.builder().userId(userId).build();
        } else {
            this.user = null;
        }
    }

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
