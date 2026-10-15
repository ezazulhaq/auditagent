package com.cb.auditagent.repository;

import com.cb.auditagent.entity.VerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface VerificationResultRepository extends JpaRepository<VerificationResult, String> {
    @Query("SELECT v FROM VerificationResult v WHERE v.run.runId = :runId ORDER BY v.createdAt ASC")
    List<VerificationResult> findByRunIdOrderByCreatedAtAsc(@Param("runId") String runId);

    @Modifying
    @Query("UPDATE VerificationResult v SET v.evidenceRedacted = NULL WHERE v.createdAt < :cutoff")
    int nullifyExpiredEvidence(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM VerificationResult v WHERE v.run.runId IN :runIds")
    void deleteByRunIdIn(@Param("runIds") Collection<String> runIds);
}
