package com.cb.auditagent.repository;

import com.cb.auditagent.entity.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, String> {
    @Query("SELECT COALESCE(MAX(s.sequenceNumber), 0) FROM AgentStep s WHERE s.runId = :runId")
    int findMaxSequenceNumberByRunId(@Param("runId") String runId);

    boolean existsByRunIdAndIdempotencyKey(String runId, String idempotencyKey);

    boolean existsByRunIdAndIdempotencyKeyAndResultStatus(String runId, String idempotencyKey, String resultStatus);

    List<AgentStep> findByRunIdOrderBySequenceNumberAsc(String runId);

    @Modifying
    @Query("DELETE FROM AgentStep s WHERE s.expiresAt < :now")
    int deleteExpiredSteps(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AgentStep s WHERE s.runId IN :runIds")
    void deleteByRunIdIn(@Param("runIds") Collection<String> runIds);
}
