package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_user_sessions_user", columnList = "user_id")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {
    @Id
    @EqualsAndHashCode.Include
    private String sessionHash;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String userId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String csrfToken;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime expiresAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime lastSeenAt;

}
