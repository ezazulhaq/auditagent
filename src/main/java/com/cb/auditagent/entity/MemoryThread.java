package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String clientId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String repoPath;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String state;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime lastAccessedAt;

}
