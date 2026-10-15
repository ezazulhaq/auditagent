package com.cb.auditagent.repository;

import com.cb.auditagent.entity.SubagentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubagentStateRepository extends JpaRepository<SubagentState, String> {
    @Query("SELECT s FROM SubagentState s WHERE s.run.runId = :runId AND s.childAgent = :childAgent AND s.status = :status ORDER BY s.sequenceNumber DESC")
    List<SubagentState> findStates(@Param("runId") String runId, @Param("childAgent") String childAgent, @Param("status") String status);

    default Optional<SubagentState> findFirstByRunIdAndChildAgentAndStatusOrderBySequenceNumberDesc(String runId, String childAgent, String status) {
        return findStates(runId, childAgent, status).stream().findFirst();
    }

    @Modifying
    @Query("UPDATE SubagentState s SET s.contextJson = NULL, s.resultJson = NULL WHERE s.expiresAt < :now")
    int nullifyExpiredData(@Param("now") LocalDateTime now);
}
