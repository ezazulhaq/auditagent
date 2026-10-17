package com.cb.auditagent.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "memory_threads",
    uniqueConstraints = { @UniqueConstraint(name = "uq_memory_thread_client_repo", columnNames = {"client_id", "repo_path"}) })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryThread {
    @Id
    @EqualsAndHashCode.Include
    private String threadId;

    @NotNull
    @Column(nullable = false)
    private String clientId;

    @NotNull
    @Column(nullable = false)
    private String repoPath;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @NotNull
    @Column(nullable = false)
    private String state;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime lastAccessedAt;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MemoryMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AgentRun> runs = new ArrayList<>();

}

