package com.cb.auditagent.repository;

import com.cb.auditagent.entity.OauthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OauthStateRepository extends JpaRepository<OauthState, String> {
    Optional<OauthState> findByStateHashAndExpiresAtAfter(String stateHash, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM OauthState s WHERE s.expiresAt < :now")
    int deleteExpiredStates(@Param("now") LocalDateTime now);
}
