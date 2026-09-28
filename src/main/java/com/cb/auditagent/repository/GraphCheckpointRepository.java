package com.cb.auditagent.repository;

import com.cb.auditagent.entity.GraphCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GraphCheckpointRepository extends JpaRepository<GraphCheckpoint, String> {
    @Query("SELECT c FROM GraphCheckpoint c WHERE c.checkpointId = :checkpointId AND (c.expiresAt IS NULL OR c.expiresAt > :now) AND c.stateJson IS NOT NULL")
    Optional<GraphCheckpoint> findValidCheckpoint(@Param("checkpointId") String checkpointId, @Param("now") LocalDateTime now);

    Optional<GraphCheckpoint> findFirstByRunIdAndStateJsonIsNotNullOrderByCreatedAtDesc(String runId);

    @Query("SELECT c.checkpointId FROM GraphCheckpoint c WHERE c.threadId = :threadId ORDER BY c.createdAt ASC")
    List<String> findCheckpointIdsByThreadIdOrderByCreatedAtAsc(@Param("threadId") String threadId);

    @Modifying
    @Query("UPDATE GraphCheckpoint c SET c.stateJson = NULL WHERE c.expiresAt < :now")
    int nullifyExpiredStateJson(@Param("now") LocalDateTime now);

    void deleteByThreadId(String threadId);
}
