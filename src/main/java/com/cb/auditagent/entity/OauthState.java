package com.cb.auditagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotNull
    @Column(columnDefinition = "TEXT", nullable = false)
    private String verifierEncrypted;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime expiresAt;

}
