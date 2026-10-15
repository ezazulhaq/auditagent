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
    @Query("SELECT COALESCE(MAX(s.sequenceNumber), 0) FROM AgentStep s WHERE s.run.runId = :runId")
    int findMaxSequenceNumberByRunId(@Param("runId") String runId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM AgentStep s WHERE s.run.runId = :runId AND s.idempotencyKey = :idempotencyKey")
    boolean existsByRunIdAndIdempotencyKey(@Param("runId") String runId, @Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM AgentStep s WHERE s.run.runId = :runId AND s.idempotencyKey = :idempotencyKey AND s.resultStatus = :resultStatus")
    boolean existsByRunIdAndIdempotencyKeyAndResultStatus(@Param("runId") String runId, @Param("idempotencyKey") String idempotencyKey, @Param("resultStatus") String resultStatus);

    @Query("SELECT s FROM AgentStep s WHERE s.run.runId = :runId ORDER BY s.sequenceNumber ASC")
    List<AgentStep> findByRunIdOrderBySequenceNumberAsc(@Param("runId") String runId);

    @Modifying
    @Query("DELETE FROM AgentStep s WHERE s.expiresAt < :now")
    int deleteExpiredSteps(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AgentStep s WHERE s.run.runId IN :runIds")
    void deleteByRunIdIn(@Param("runIds") Collection<String> runIds);
}
