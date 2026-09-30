package com.cb.auditagent.repository;

import com.cb.auditagent.entity.MemoryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface MemoryMessageRepository extends JpaRepository<MemoryMessage, String> {
    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM MemoryMessage m WHERE m.threadId = :threadId")
    int findMaxSequenceNumberByThreadId(@Param("threadId") String threadId);

    @Query("SELECT m FROM MemoryMessage m WHERE m.threadId = :threadId AND (m.expiresAt IS NULL OR m.expiresAt > :now) ORDER BY m.sequenceNumber ASC")
    List<MemoryMessage> findActiveByThreadId(@Param("threadId") String threadId, @Param("now") LocalDateTime now);

    @Query("SELECT m FROM MemoryMessage m WHERE m.threadId = :threadId AND (m.expiresAt IS NULL OR m.expiresAt > :now) AND ((:runId IS NULL AND m.runId IS NULL) OR m.runId = :runId OR (:includeGeneral = TRUE AND m.runId IS NULL)) ORDER BY m.sequenceNumber ASC")
    List<MemoryMessage> findFilteredMessages(@Param("threadId") String threadId, @Param("runId") String runId,
            @Param("includeGeneral") boolean includeGeneral, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM MemoryMessage m WHERE m.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") String threadId);

    @Modifying
    @Query("DELETE FROM MemoryMessage m WHERE m.expiresAt < :now")
    int deleteExpiredMessages(@Param("now") LocalDateTime now);
}
