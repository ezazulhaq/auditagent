package com.cb.auditagent.repository;

import com.cb.auditagent.entity.RepositoryMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepositoryMemoryRepository extends JpaRepository<RepositoryMemory, String> {
    Optional<RepositoryMemory> findByRepoPathAndFindingFingerprint(String repoPath, String findingFingerprint);
    List<RepositoryMemory> findByApprovedAndRepoPath(Boolean approved, String repoPath);

    @Modifying
    @Query("DELETE FROM RepositoryMemory m WHERE m.repoPath = :repoPath")
    void deleteByRepoPath(@Param("repoPath") String repoPath);

    @Modifying
    @Query("UPDATE RepositoryMemory m SET m.confidence = m.confidence * 0.5, m.updatedAt = :now WHERE m.sourceRunId = :sourceRunId OR m.findingFingerprint = :fingerprint")
    int lowerConfidence(@Param("sourceRunId") String sourceRunId, @Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RepositoryMemory m SET m.usageCount = m.usageCount + 1 WHERE m.memoryId = :memoryId")
    int incrementUsageCount(@Param("memoryId") String memoryId);
}
