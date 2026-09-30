package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {
    @Id
    @EqualsAndHashCode.Include
    private String deliveryId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String eventType;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime receivedAt;

}
