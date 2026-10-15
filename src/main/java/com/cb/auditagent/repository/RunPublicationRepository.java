package com.cb.auditagent.repository;

import com.cb.auditagent.entity.RunPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RunPublicationRepository extends JpaRepository<RunPublication, String> {
    @Query("SELECT p FROM RunPublication p WHERE p.repository.repositoryId = :repositoryId AND p.prNumber = :prNumber")
    Optional<RunPublication> findByRepositoryIdAndPrNumber(@Param("repositoryId") Long repositoryId, @Param("prNumber") Integer prNumber);

    @Query("SELECT p.runId FROM RunPublication p, AgentRun r WHERE r.runId = p.runId AND p.repository.repositoryId = :repositoryId AND p.baseBranch = :baseBranch AND r.findingFingerprint = :fingerprint AND r.status IN :statuses")
    List<String> findActiveManagedRunIds(@Param("repositoryId") Long repositoryId, @Param("baseBranch") String baseBranch, @Param("fingerprint") String fingerprint, @Param("statuses") Collection<String> statuses);

    @Query("SELECT p FROM RunPublication p, AgentRun r WHERE p.runId = r.runId AND r.status IN :statuses AND r.updatedAt < :cutoff")
    List<RunPublication> findStaleWorkspaces(@Param("statuses") Collection<String> statuses, @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT p FROM RunPublication p WHERE p.repository.repositoryId = :repositoryId")
    List<RunPublication> findByRepositoryId(@Param("repositoryId") Long repositoryId);
}
