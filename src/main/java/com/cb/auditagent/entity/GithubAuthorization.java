package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "github_authorizations")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubAuthorization {
    @Id
    @EqualsAndHashCode.Include
    private String userId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    private java.time.LocalDateTime accessExpiresAt;

    @Column(columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    private java.time.LocalDateTime refreshExpiresAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
