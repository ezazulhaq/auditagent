package com.cb.auditagent.repository;

import com.cb.auditagent.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findBySessionHashAndExpiresAtAfter(String sessionHash, LocalDateTime now);

    @Query("SELECT s FROM UserSession s JOIN FETCH s.user WHERE s.sessionHash = :sessionHash AND s.expiresAt > :now")
    Optional<UserSession> findWithUserBySessionHashAndExpiresAtAfter(@Param("sessionHash") String sessionHash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);
}
