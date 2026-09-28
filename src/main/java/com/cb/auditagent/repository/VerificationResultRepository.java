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
    List<VerificationResult> findByRunIdOrderByCreatedAtAsc(String runId);

    @Modifying
    @Query("UPDATE VerificationResult v SET v.evidenceRedacted = NULL WHERE v.createdAt < :cutoff")
    int nullifyExpiredEvidence(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM VerificationResult v WHERE v.runId IN :runIds")
    void deleteByRunIdIn(@Param("runIds") Collection<String> runIds);
}
