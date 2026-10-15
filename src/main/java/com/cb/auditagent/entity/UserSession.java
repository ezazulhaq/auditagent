package com.cb.auditagent.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    @NotNull
    @Column(nullable = false)
    private String csrfToken;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime expiresAt;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime lastSeenAt;

    public String getUserId() {
        return user != null ? user.getUserId() : null;
    }

    public void setUserId(String userId) {
        if (userId != null) {
            this.user = AppUser.builder().userId(userId).build();
        } else {
            this.user = null;
        }
    }

}
