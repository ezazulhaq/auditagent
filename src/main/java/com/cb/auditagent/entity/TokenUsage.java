package com.cb.auditagent.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "token_usage")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsage {
    @Id
    @EqualsAndHashCode.Include
    private String id;

    @NotNull
    @Column(nullable = false)
    private String userId;

    private String repoPath;

    private String repoName;

    private String vulnerabilityId;

    private String threadId;

    private String runId;

    @NotNull
    @Column(nullable = false)
    private String modelName;

    @NotNull
    @Column(nullable = false)
    private Integer tokens;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
