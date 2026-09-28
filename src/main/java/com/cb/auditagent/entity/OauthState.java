package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "oauth_states")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthState {
    @Id
    @EqualsAndHashCode.Include
    private String stateHash;

    @jakarta.validation.constraints.NotNull
    @Column(columnDefinition = "TEXT", nullable = false)
    private String verifierEncrypted;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime expiresAt;

}
