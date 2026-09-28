package com.cb.auditagent.repository;

import com.cb.auditagent.entity.SubagentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SubagentStateRepository extends JpaRepository<SubagentState, String> {
    Optional<SubagentState> findFirstByRunIdAndChildAgentAndStatusOrderBySequenceNumberDesc(String runId, String childAgent, String status);

    @Modifying
    @Query("UPDATE SubagentState s SET s.contextJson = NULL, s.resultJson = NULL WHERE s.expiresAt < :now")
    int nullifyExpiredData(@Param("now") LocalDateTime now);
}
