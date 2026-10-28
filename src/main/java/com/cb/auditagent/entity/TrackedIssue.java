package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracked_issues", indexes = {
    @Index(name = "idx_tracked_issues_repo", columnList = "repository_id"),
    @Index(name = "idx_tracked_issues_repo_issue", columnList = "repository_id, issue_number"),
    @Index(name = "idx_tracked_issues_pr", columnList = "pull_request_number")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedIssue {
    @Id
    @EqualsAndHashCode.Include
    private String id; // format: "repoId-issueNumber"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ManagedRepository repository;

    @Column(nullable = false)
    private Integer issueNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String labels;

    @Column(nullable = false)
    private String state; // "OPEN" or "CLOSED"

    private String targetVulnerabilityId;

    private String runId;

    private String remediationStatus;

    private Integer pullRequestNumber;

    private String pullRequestUrl;

    @Column(columnDefinition = "TEXT")
    private String statusMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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

    public static class TrackedIssueBuilder {
        public TrackedIssueBuilder repositoryId(Long repositoryId) {
            if (repositoryId != null) {
                this.repository = ManagedRepository.builder().repositoryId(repositoryId).build();
            } else {
                this.repository = null;
            }
            return this;
        }
    }
}
