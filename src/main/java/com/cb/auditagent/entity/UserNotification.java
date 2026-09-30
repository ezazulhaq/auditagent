package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_notifications",
    indexes = { @Index(name = "idx_user_notification_user", columnList = "user_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {
    @Id
    @EqualsAndHashCode.Include
    private String id;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String userId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String type;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Boolean isRead;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
