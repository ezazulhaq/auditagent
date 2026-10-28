package com.cb.auditagent.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "managed_repositories")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagedRepository {
    @Id
    @EqualsAndHashCode.Include
    private Long repositoryId;

    @NotNull
    @Column(nullable = false)
    private Long installationId;

    @NotNull
    @Column(nullable = false)
    private String ownerLogin;

    @NotNull
    @Column(nullable = false)
    private String repoName;

    @NotNull
    @Column(nullable = false)
    private String fullName;

    @NotNull
    @Column(length = 1024, nullable = false)
    private String cloneUrl;

    @NotNull
    @Column(nullable = false)
    private String defaultBranch;

    @NotNull
    @Column(nullable = false)
    private Boolean isPrivate;

    private String permission;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ScanSnapshot> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ScanHistoryStats> historyStats = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TrackedIssue> trackedIssues = new ArrayList<>();

}
