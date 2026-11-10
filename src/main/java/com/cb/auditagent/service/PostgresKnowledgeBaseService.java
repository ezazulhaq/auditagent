package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.repository.RepositoryMemoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * PostgreSQL Knowledge Base service for repository memory retrieval.
 * Replaces the embedded DuckDB knowledge base with scalable PostgreSQL-backed Full-Text Search
 * and metadata candidate retrieval.
 */
@Service
@Primary
public class PostgresKnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(PostgresKnowledgeBaseService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RepositoryMemoryRepository memoryRepository;
    private final MemoryConfig memoryConfig;

    public PostgresKnowledgeBaseService(JdbcTemplate jdbcTemplate,
                                       RepositoryMemoryRepository memoryRepository,
                                       MemoryConfig memoryConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryRepository = memoryRepository;
        this.memoryConfig = memoryConfig;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public boolean isFtsAvailable() {
        return true;
    }

    public void ensureFtsIndex() {
        // PostgreSQL GIN indexes and tables are managed via JPA/Hibernate DDL or migrations.
        logger.debug("PostgreSQL knowledge base search is active.");
    }

    public void syncRepositoryMemory(RepositoryMemory memory) {
        // Memory is already persisted via RepositoryMemoryRepository in PostgreSQL.
    }

    public String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return Path.of(path).normalize().toString();
    }

    /**
     * Search for approved repository memories using PostgreSQL FTS and metadata ranking.
     */
    public List<RepositoryMemory> searchRepositoryMemories(String repoPath, Vulnerability vulnerability) {
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();

        if (query != null && !query.isBlank()) {
            loadFtsCandidates(normalizedRepo, query, true, candidates);
        }
        loadMetadataCandidates(normalizedRepo, vulnerability, true, candidates);

        List<RepositoryMemory> ranked = candidates.values().stream()
                .map(m -> withScore(m, calculateMemoryScore(m, vulnerability)))
                .sorted(Comparator.comparingDouble(RepositoryMemory::score).reversed())
                .limit(memoryConfig.getTopK())
                .toList();

        if (!ranked.isEmpty()) {
            incrementMemoryUsage(ranked);
        }
        return ranked;
    }

    /**
     * Search for rejected repository memories for negative-example retrieval.
     */
    public List<RepositoryMemory> searchRejectedMemories(String repoPath, Vulnerability vulnerability) {
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();

        if (query != null && !query.isBlank()) {
            loadFtsCandidates(normalizedRepo, query, false, candidates);
        }
        loadMetadataCandidates(normalizedRepo, vulnerability, false, candidates);

        return candidates.values().stream()
                .map(m -> withScore(m, calculateMemoryScore(m, vulnerability)))
                .sorted(Comparator.comparingDouble(RepositoryMemory::score).reversed())
                .limit(memoryConfig.getTopK())
                .toList();
    }

    private void loadFtsCandidates(String repoPath, String query, boolean approved,
                                   Map<String, RepositoryMemory> candidates) {
        int candidateLimit = memoryConfig.getCandidateLimit();
        // Try native PostgreSQL tsvector / plainto_tsquery if available
        String sql = "SELECT memory_id, repo_path, finding_fingerprint, rule_id, category, " +
                "vulnerability_type, language, framework, file_pattern, title, summary, root_cause, " +
                "remediation_pattern, search_text, confidence, source_run_id, usage_count, updated_at, " +
                "ts_rank_cd(to_tsvector('english', coalesce(title, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(root_cause, '') || ' ' || coalesce(remediation_pattern, '') || ' ' || coalesce(search_text, '')), plainto_tsquery('english', ?)) AS score " +
                "FROM repository_memories " +
                "WHERE repo_path = ? AND approved = ? " +
                "AND to_tsvector('english', coalesce(title, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(root_cause, '') || ' ' || coalesce(remediation_pattern, '') || ' ' || coalesce(search_text, '')) @@ plainto_tsquery('english', ?) " +
                "ORDER BY score DESC LIMIT ?";
        try {
            jdbcTemplate.query(sql, rs -> {
                String memoryId = rs.getString("memory_id");
                if (!candidates.containsKey(memoryId)) {
                    candidates.put(memoryId, new RepositoryMemory(
                            rs.getString("memory_id"), rs.getString("repo_path"),
                            rs.getString("finding_fingerprint"), rs.getString("rule_id"),
                            rs.getString("category"), rs.getString("vulnerability_type"),
                            rs.getString("language"), rs.getString("framework"),
                            rs.getString("file_pattern"), rs.getString("title"),
                            rs.getString("summary"), rs.getString("root_cause"),
                            rs.getString("remediation_pattern"), rs.getString("search_text"),
                            rs.getDouble("confidence"), rs.getString("source_run_id"),
                            rs.getInt("usage_count"),
                            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                            rs.getDouble("score")));
                }
            }, query, repoPath, approved, query, candidateLimit);
        } catch (Exception e) {
            logger.debug("PostgreSQL native tsvector query failed ({}), falling back to text term matching", e.getMessage());
            // Fallback for non-PostgreSQL (e.g. H2 test DB) or simpler search
            String[] tokens = query.split("\\s+");
            String primaryTerm = tokens.length > 0 ? tokens[0] : query;
            List<com.cb.auditagent.entity.RepositoryMemory> textMatches = memoryRepository.findTextCandidates(
                    repoPath, approved, primaryTerm, PageRequest.of(0, candidateLimit));
            for (com.cb.auditagent.entity.RepositoryMemory entity : textMatches) {
                if (!candidates.containsKey(entity.getMemoryId())) {
                    candidates.put(entity.getMemoryId(), mapEntityToDomain(entity, 1.0));
                }
            }
        }
    }

    private void loadMetadataCandidates(String repoPath, Vulnerability vulnerability, boolean approved,
                                        Map<String, RepositoryMemory> candidates) {
        int candidateLimit = memoryConfig.getCandidateLimit();
        List<com.cb.auditagent.entity.RepositoryMemory> metadataMatches = memoryRepository.findMetadataCandidates(
                repoPath, approved,
                vulnerability.getFindingFingerprint(),
                vulnerability.getRuleId(),
                vulnerability.getVulnType(),
                vulnerability.getLanguage(),
                PageRequest.of(0, candidateLimit));

        for (com.cb.auditagent.entity.RepositoryMemory entity : metadataMatches) {
            if (!candidates.containsKey(entity.getMemoryId())) {
                candidates.put(entity.getMemoryId(), mapEntityToDomain(entity, 0.0));
            }
        }
    }

    private RepositoryMemory mapEntityToDomain(com.cb.auditagent.entity.RepositoryMemory entity, double score) {
        return new RepositoryMemory(
                entity.getMemoryId(), entity.getRepoPath(),
                entity.getFindingFingerprint(), entity.getRuleId(),
                entity.getCategory(), entity.getVulnerabilityType(),
                entity.getLanguage(), entity.getFramework(),
                entity.getFilePattern(), entity.getTitle(),
                entity.getSummary(), entity.getRootCause(),
                entity.getRemediationPattern(), entity.getSearchText(),
                entity.getConfidence(), entity.getSourceRunId(),
                entity.getUsageCount(), entity.getUpdatedAt(),
                score);
    }

    private double calculateMemoryScore(RepositoryMemory memory, Vulnerability vulnerability) {
        double score = Math.min(Math.max(memory.score(), 0.0), 1_000.0) * 1_000.0;
        if (notBlankEquals(memory.findingFingerprint(), vulnerability.getFindingFingerprint()))
            score += 1_000_000_000;
        if (notBlankEquals(memory.ruleId(), vulnerability.getRuleId()))
            score += 100_000_000;
        SecurityConceptCatalog.Concept concept = SecurityConceptCatalog.describe(vulnerability);
        if (notBlankEquals(memory.category(), concept.category()))
            score += 10_000_000;
        if (notBlankEquals(memory.vulnerabilityType(), vulnerability.getVulnType()))
            score += 5_000_000;
        if (notBlankEquals(memory.language(), vulnerability.getLanguage()))
            score += 500;
        if (notBlankEquals(memory.framework(), concept.framework()))
            score += 400;
        if (SecurityConceptCatalog.matchesPattern(memory.filePattern(), vulnerability.getFilePath()))
            score += 300;
        long ageDays = memory.updatedAt() == null ? 365
                : Math.max(0, ChronoUnit.DAYS.between(memory.updatedAt(), LocalDateTime.now()));
        double recency = Math.max(0, 30 - Math.min(ageDays, 30));
        return score + memory.confidence() * 100 + Math.min(memory.usageCount(), 50) + recency;
    }

    private boolean notBlankEquals(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private RepositoryMemory withScore(RepositoryMemory m, double score) {
        return new RepositoryMemory(m.memoryId(), m.repoPath(), m.findingFingerprint(), m.ruleId(), m.category(),
                m.vulnerabilityType(), m.language(), m.framework(), m.filePattern(), m.title(), m.summary(),
                m.rootCause(), m.remediationPattern(), m.searchText(), m.confidence(), m.sourceRunId(),
                m.usageCount(), m.updatedAt(), score);
    }

    private void incrementMemoryUsage(List<RepositoryMemory> memories) {
        try {
            for (RepositoryMemory memory : memories) {
                memoryRepository.incrementUsageCount(memory.memoryId());
            }
        } catch (Exception e) {
            logger.warn("Could not increment memory usage count: {}", e.getMessage());
        }
    }
}
