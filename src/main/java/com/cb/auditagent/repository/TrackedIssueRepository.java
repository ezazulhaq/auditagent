package com.cb.auditagent.repository;

import com.cb.auditagent.entity.TrackedIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedIssueRepository extends JpaRepository<TrackedIssue, String> {
    @Query("SELECT t FROM TrackedIssue t WHERE t.repository.repositoryId = :repositoryId ORDER BY t.updatedAt DESC")
    List<TrackedIssue> findByRepositoryIdOrderByUpdatedAtDesc(@Param("repositoryId") Long repositoryId);

    @Query("SELECT t FROM TrackedIssue t WHERE t.repository.repositoryId = :repositoryId AND t.issueNumber = :issueNumber")
    Optional<TrackedIssue> findByRepositoryIdAndIssueNumber(@Param("repositoryId") Long repositoryId, @Param("issueNumber") Integer issueNumber);

    Optional<TrackedIssue> findByPullRequestNumber(Integer pullRequestNumber);
}
