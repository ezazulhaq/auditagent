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
    @Query("SELECT r FROM AgentRun r WHERE r.thread.threadId = :threadId AND r.status IN :statuses ORDER BY r.updatedAt DESC")
    List<AgentRun> findByThreadIdAndStatusInOrderByUpdatedAtDesc(@Param("threadId") String threadId, @Param("statuses") Collection<String> statuses);

    default Optional<AgentRun> findFirstByThreadIdAndStatusInOrderByUpdatedAtDesc(String threadId, Collection<String> statuses) {
        return findByThreadIdAndStatusInOrderByUpdatedAtDesc(threadId, statuses).stream().findFirst();
    }

    @Query("SELECT r FROM AgentRun r WHERE (r.thread.threadId = :threadId OR r.repoPath = :repoPath) AND r.status IN :statuses")
    List<AgentRun> findActiveRunsByThreadOrRepo(@Param("threadId") String threadId, @Param("repoPath") String repoPath,
            @Param("statuses") Collection<String> statuses);

    @Query("SELECT r FROM AgentRun r LEFT JOIN r.vulnerability v WHERE ((:vulnId IS NOT NULL AND v.id = :vulnId) OR (:fingerprint IS NOT NULL AND r.findingFingerprint = :fingerprint) OR (:workspacePath IS NOT NULL AND r.repoPath = :workspacePath)) AND r.status IN :statuses")
    List<AgentRun> findActiveRunsByVulnOrWorkspace(@Param("vulnId") String vulnId, @Param("fingerprint") String fingerprint,
            @Param("workspacePath") String workspacePath, @Param("statuses") Collection<String> statuses);

    @Query("SELECT r FROM AgentRun r WHERE r.thread.threadId = :threadId")
    List<AgentRun> findByThreadId(@Param("threadId") String threadId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AgentRun r WHERE r.thread.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") String threadId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.errorDetail = NULL WHERE r.updatedAt < :cutoff")
    int clearExpiredErrorDetails(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.checkpointJson = :checkpointJson, r.updatedAt = :now WHERE r.runId = :runId")
    int updateCheckpointJson(@Param("runId") String runId, @Param("checkpointJson") String checkpointJson,
            @Param("now") LocalDateTime now);
}
