package com.cb.auditagent.repository;

import com.cb.auditagent.entity.ScanSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScanSnapshotRepository extends JpaRepository<ScanSnapshot, String> {
    Optional<ScanSnapshot> findFirstByRepositoryIdAndBranchOrderByCreatedAtDesc(Long repositoryId, String branch);
}
