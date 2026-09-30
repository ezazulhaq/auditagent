package com.cb.auditagent.repository;

import com.cb.auditagent.entity.MemoryThread;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MemoryThreadRepository extends JpaRepository<MemoryThread, String> {
    Optional<MemoryThread> findByClientIdAndRepoPath(String clientId, String repoPath);
    boolean existsByThreadIdAndRepoPath(String threadId, String repoPath);
    boolean existsByThreadIdAndClientId(String threadId, String clientId);
    List<MemoryThread> findByClientIdOrderByLastAccessedAtDesc(String clientId);
}
