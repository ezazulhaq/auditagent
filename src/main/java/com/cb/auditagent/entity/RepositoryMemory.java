package com.cb.auditagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotNull
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

    @NotNull
    @Column(nullable = false)
    private Double confidence;

    @NotNull
    @Column(nullable = false)
    private String sourceRunId;

    @NotNull
    @Column(nullable = false)
    private Boolean approved;

    @NotNull
    @Column(nullable = false)
    private Integer usageCount;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
