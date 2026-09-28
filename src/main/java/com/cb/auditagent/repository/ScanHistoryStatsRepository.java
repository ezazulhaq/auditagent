package com.cb.auditagent.repository;

import com.cb.auditagent.entity.ScanHistoryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScanHistoryStatsRepository extends JpaRepository<ScanHistoryStats, String> {
    List<ScanHistoryStats> findTop50ByRepositoryIdAndBranchOrderByCreatedAtDesc(Long repositoryId, String branch);
}
