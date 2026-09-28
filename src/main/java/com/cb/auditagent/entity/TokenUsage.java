package com.cb.auditagent.entity;

import jakarta.persistence.*;
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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String userId;

    private String repoPath;

    private String vulnerabilityId;

    private String threadId;

    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String modelName;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer tokens;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
