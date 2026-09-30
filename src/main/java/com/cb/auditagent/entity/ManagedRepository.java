package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long installationId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String ownerLogin;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String repoName;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String fullName;

    @jakarta.validation.constraints.NotNull
    @Column(length = 1024, nullable = false)
    private String cloneUrl;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String defaultBranch;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean isPrivate;

    private String permission;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
