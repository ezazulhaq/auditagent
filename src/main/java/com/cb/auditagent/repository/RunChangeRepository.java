package com.cb.auditagent.repository;

import com.cb.auditagent.entity.RunChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface RunChangeRepository extends JpaRepository<RunChange, String> {
    @Query("SELECT c FROM RunChange c WHERE c.run.runId = :runId ORDER BY c.createdAt DESC")
    List<RunChange> findByRunIdOrderByCreatedAtDesc(@Param("runId") String runId);

    @Modifying
    @Query("UPDATE RunChange c SET c.state = :state WHERE c.changeId = :changeId")
    int updateState(@Param("changeId") String changeId, @Param("state") String state);

    @Modifying
    @Query("DELETE FROM RunChange c WHERE c.run.runId IN :runIds")
    void deleteByRunIdIn(@Param("runIds") Collection<String> runIds);
}
