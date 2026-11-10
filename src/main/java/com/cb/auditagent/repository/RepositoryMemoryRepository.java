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

    @Query("SELECT m FROM RepositoryMemory m WHERE m.repoPath = :repoPath AND m.approved = :approved AND (" +
           "m.findingFingerprint = :fingerprint OR m.ruleId = :ruleId OR m.vulnerabilityType = :vulnType OR m.language = :language) " +
           "ORDER BY m.confidence DESC, m.updatedAt DESC")
    List<RepositoryMemory> findMetadataCandidates(@Param("repoPath") String repoPath,
                                                  @Param("approved") Boolean approved,
                                                  @Param("fingerprint") String fingerprint,
                                                  @Param("ruleId") String ruleId,
                                                  @Param("vulnType") String vulnType,
                                                  @Param("language") String language,
                                                  org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM RepositoryMemory m WHERE m.repoPath = :repoPath AND m.approved = :approved AND (" +
           "LOWER(m.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.summary) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.rootCause) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.remediationPattern) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.searchText) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<RepositoryMemory> findTextCandidates(@Param("repoPath") String repoPath,
                                              @Param("approved") Boolean approved,
                                              @Param("term") String term,
                                              org.springframework.data.domain.Pageable pageable);
}
