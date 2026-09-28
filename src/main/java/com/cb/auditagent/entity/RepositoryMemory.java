package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "repository_memories",
    uniqueConstraints = { @UniqueConstraint(name = "uq_repository_memory_fingerprint", columnNames = {"repo_path", "finding_fingerprint"}) })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryMemory {
    @Id
    @EqualsAndHashCode.Include
    private String memoryId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String repoPath;

    private String findingFingerprint;

    private String ruleId;

    private String category;

    private String vulnerabilityType;

    private String language;

    private String framework;

    private String filePattern;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String remediationPattern;

    @Column(columnDefinition = "TEXT")
    private String searchText;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Double confidence;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String sourceRunId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean approved;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer usageCount;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
