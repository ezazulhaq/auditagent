package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_llm_configs")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLlmConfig {

    @Id
    @EqualsAndHashCode.Include
    private String userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AppUser user;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String provider; // "OPENROUTER" or "OPENAI_COMPATIBLE"

    @Column(columnDefinition = "TEXT")
    private String baseUrl;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String modelName;

    private String supervisorModelName;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

