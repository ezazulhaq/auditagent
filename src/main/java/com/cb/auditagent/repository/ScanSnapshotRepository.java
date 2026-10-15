package com.cb.auditagent.repository;

import com.cb.auditagent.entity.ScanSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ScanSnapshotRepository extends JpaRepository<ScanSnapshot, String> {
    @Query("SELECT s FROM ScanSnapshot s WHERE s.repository.repositoryId = :repositoryId AND s.branch = :branch ORDER BY s.createdAt DESC")
    List<ScanSnapshot> findSnapshots(@Param("repositoryId") Long repositoryId, @Param("branch") String branch);

    default Optional<ScanSnapshot> findFirstByRepositoryIdAndBranchOrderByCreatedAtDesc(Long repositoryId, String branch) {
        return findSnapshots(repositoryId, branch).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM ScanSnapshot s WHERE s.repository.repositoryId = :repositoryId AND s.branch = :branch")
    void deleteByRepositoryIdAndBranch(@Param("repositoryId") Long repositoryId, @Param("branch") String branch);
}
