package com.cb.auditagent.repository;

import com.cb.auditagent.entity.ScanHistoryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ScanHistoryStatsRepository extends JpaRepository<ScanHistoryStats, String> {
    @Query("SELECT s FROM ScanHistoryStats s WHERE s.repository.repositoryId = :repositoryId AND s.branch = :branch ORDER BY s.createdAt DESC")
    List<ScanHistoryStats> findHistoryStats(@Param("repositoryId") Long repositoryId, @Param("branch") String branch);

    default List<ScanHistoryStats> findTop50ByRepositoryIdAndBranchOrderByCreatedAtDesc(Long repositoryId, String branch) {
        List<ScanHistoryStats> list = findHistoryStats(repositoryId, branch);
        return list.size() > 50 ? list.subList(0, 50) : list;
    }

    @Modifying
    @Query("DELETE FROM ScanHistoryStats s WHERE s.repository.repositoryId = :repositoryId AND s.branch = :branch")
    void deleteByRepositoryIdAndBranch(@Param("repositoryId") Long repositoryId, @Param("branch") String branch);
}
