package com.cb.auditagent.repository;

import com.cb.auditagent.entity.GraphNodeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface GraphNodeEventRepository extends JpaRepository<GraphNodeEvent, String> {
    List<GraphNodeEvent> findByRunIdOrderByCreatedAtAsc(String runId);

    @Modifying
    @Query("UPDATE GraphNodeEvent e SET e.detailJson = NULL WHERE e.expiresAt < :now")
    int nullifyExpiredDetailJson(@Param("now") LocalDateTime now);
}
