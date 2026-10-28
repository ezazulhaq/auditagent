package com.cb.auditagent.repository;

import com.cb.auditagent.entity.TrackedIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedIssueRepository extends JpaRepository<TrackedIssue, String> {
    List<TrackedIssue> findByRepositoryIdOrderByUpdatedAtDesc(Long repositoryId);
    Optional<TrackedIssue> findByRepositoryIdAndIssueNumber(Long repositoryId, Integer issueNumber);
    Optional<TrackedIssue> findByPullRequestNumber(Integer pullRequestNumber);
}
