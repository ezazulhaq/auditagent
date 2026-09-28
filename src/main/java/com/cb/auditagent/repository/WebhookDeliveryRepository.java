package com.cb.auditagent.repository;

import com.cb.auditagent.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {
    @Modifying
    @Query("DELETE FROM WebhookDelivery w WHERE w.receivedAt < :cutoff")
    int deleteOldDeliveries(@Param("cutoff") LocalDateTime cutoff);
}
