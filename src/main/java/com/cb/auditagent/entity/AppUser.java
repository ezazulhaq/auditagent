package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {
    @Id
    @EqualsAndHashCode.Include
    private String userId;

    @jakarta.validation.constraints.NotNull
    @Column(unique = true, nullable = false)
    private Long githubUserId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String login;

    private String displayName;

    private String avatarUrl;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
