package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracked_issues")
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

    @Column(nullable = false)
    private Long repositoryId;

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
}
