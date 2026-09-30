package com.cb.auditagent.repository;

import com.cb.auditagent.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, String> {
    List<UserNotification> findTop100ByUserIdOrderByCreatedAtDesc(String userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true WHERE n.userId = :userId")
    int markAllAsReadByUserId(@Param("userId") String userId);
}
