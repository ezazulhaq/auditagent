package com.cb.auditagent.service.data;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.MemoryMessageRecord;
import com.cb.auditagent.entity.MemoryMessage;
import com.cb.auditagent.entity.MemoryThread;
import com.cb.auditagent.repository.MemoryMessageRepository;
import com.cb.auditagent.repository.MemoryThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain data service for Conversation aggregates (MemoryThread and MemoryMessage).
 */
@Service
public class ConversationDataService {

    private final MemoryThreadRepository memoryThreadRepository;
    private final MemoryMessageRepository memoryMessageRepository;
    private final MemoryConfig memoryConfig;

    public ConversationDataService(MemoryThreadRepository memoryThreadRepository,
                                  MemoryMessageRepository memoryMessageRepository,
                                  MemoryConfig memoryConfig) {
        this.memoryThreadRepository = memoryThreadRepository;
        this.memoryMessageRepository = memoryMessageRepository;
        this.memoryConfig = memoryConfig;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private int estimateTokens(String value) {
        if (value == null || value.isEmpty())
            return 1;
        return Math.max(1, value.length() / 4);
    }

    private MemoryMessageRecord mapToMemoryMessageRecord(MemoryMessage e) {
        if (e == null)
            return null;
        return new MemoryMessageRecord(e.getMessageId(), e.getThreadId(), e.getRunId(), e.getSequenceNumber(),
                e.getRole(), e.getMessageType(), e.getContentRedacted(), e.getMetadataJson(), e.getCreatedAt());
    }

    public String getOrCreateMemoryThread(String clientId, String repoPath) {
        Optional<MemoryThread> threadOpt = memoryThreadRepository.findByClientIdAndRepoPath(clientId, repoPath);
        if (threadOpt.isPresent()) {
            return threadOpt.get().getThreadId();
        }
        MemoryThread newThread = new MemoryThread();
        newThread.setThreadId(UUID.randomUUID().toString());
        newThread.setClientId(clientId);
        newThread.setRepoPath(repoPath);
        newThread.setState("ACTIVE");
        newThread.setCreatedAt(LocalDateTime.now());
        newThread.setUpdatedAt(LocalDateTime.now());
        newThread.setLastAccessedAt(LocalDateTime.now());
        memoryThreadRepository.save(newThread);
        return newThread.getThreadId();
    }

    public boolean memoryThreadExists(String threadId, String repoPath) {
        return memoryThreadRepository.existsByThreadIdAndRepoPath(threadId, repoPath);
    }

    @Transactional
    public void ensureMemoryThread(String threadId, String clientId, String repoPath) {
        if (memoryThreadRepository.findById(threadId).isEmpty()) {
            MemoryThread t = new MemoryThread();
            t.setThreadId(threadId);
            t.setClientId(clientId);
            t.setRepoPath(repoPath);
            t.setState("ACTIVE");
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            t.setLastAccessedAt(LocalDateTime.now());
            memoryThreadRepository.save(t);
        }
    }

    public boolean memoryThreadBelongsTo(String threadId, String userId) {
        return memoryThreadRepository.existsByThreadIdAndClientId(threadId, userId);
    }

    public Optional<MemoryThread> findThreadById(String threadId) {
        return memoryThreadRepository.findById(threadId);
    }

    @Transactional
    public MemoryMessageRecord appendMemoryMessage(String threadId, String runId, String role, String messageType,
            String content, String metadataJson) {
        Integer currentSeq = memoryMessageRepository.findMaxSequenceNumberByThreadId(threadId);
        long seq = (currentSeq == null ? 0 : currentSeq) + 1L;
        MemoryMessage msg = new MemoryMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        MemoryThread thread = memoryThreadRepository.findById(threadId).orElse(null);
        if (thread != null) {
            msg.setThread(thread);
            thread.setLastAccessedAt(LocalDateTime.now());
            memoryThreadRepository.save(thread);
        } else {
            msg.setThreadId(threadId);
        }
        msg.setRunId(runId);
        msg.setSequenceNumber(seq);
        msg.setRole(role);
        msg.setMessageType(messageType);
        msg.setContentRedacted(content);
        msg.setContentHash(sha256(content));
        msg.setTokenEstimate(estimateTokens(content));
        msg.setMetadataJson(metadataJson);
        msg.setCreatedAt(LocalDateTime.now());
        if (memoryConfig != null && memoryConfig.getRetentionDays() > 0) {
            msg.setExpiresAt(LocalDateTime.now().plusDays(memoryConfig.getRetentionDays()));
        }
        memoryMessageRepository.save(msg);

        return mapToMemoryMessageRecord(msg);
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId) {
        return memoryMessageRepository.findActiveByThreadId(threadId, LocalDateTime.now())
                .stream().map(this::mapToMemoryMessageRecord).collect(Collectors.toList());
    }

    public List<MemoryMessageRecord> getMemoryMessages(String threadId, String runId, boolean includeGeneralChat) {
        return memoryMessageRepository.findFilteredMessages(threadId, runId, includeGeneralChat, LocalDateTime.now())
                .stream().map(this::mapToMemoryMessageRecord).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMemoryThread(String threadId) {
        memoryMessageRepository.deleteByThreadId(threadId);
        memoryThreadRepository.deleteById(threadId);
    }

    @Transactional
    public void deleteExpiredMemory(LocalDateTime now) {
        memoryMessageRepository.deleteExpiredMessages(now);
    }
}
