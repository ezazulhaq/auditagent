package com.cb.auditagent.repository;

import com.cb.auditagent.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    Optional<AgentRun> findFirstByThreadIdAndStatusInOrderByUpdatedAtDesc(String threadId, Collection<String> statuses);

    @Query("SELECT r FROM AgentRun r WHERE (r.threadId = :threadId OR r.repoPath = :repoPath) AND r.status IN :statuses")
    List<AgentRun> findActiveRunsByThreadOrRepo(@Param("threadId") String threadId, @Param("repoPath") String repoPath, @Param("statuses") Collection<String> statuses);

    List<AgentRun> findByThreadId(String threadId);

    @Modifying
    @Query("DELETE FROM AgentRun r WHERE r.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") String threadId);

    @Modifying
    @Query("UPDATE AgentRun r SET r.errorDetail = NULL WHERE r.updatedAt < :cutoff")
    int clearExpiredErrorDetails(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE AgentRun r SET r.checkpointJson = :checkpointJson, r.updatedAt = :now WHERE r.runId = :runId")
    int updateCheckpointJson(@Param("runId") String runId, @Param("checkpointJson") String checkpointJson, @Param("now") LocalDateTime now);
}
