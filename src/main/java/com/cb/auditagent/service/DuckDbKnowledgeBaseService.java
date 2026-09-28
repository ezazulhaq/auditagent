package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Vulnerability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DuckDbKnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(DuckDbKnowledgeBaseService.class);

    private final String dbPath;
    private final MemoryConfig memoryConfig;
    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private volatile boolean ftsAvailable = false;
    private final ReentrantLock ftsLock = new ReentrantLock();

    public DuckDbKnowledgeBaseService(
            @Value("${auditagent.database.path:audit_reports.duckdb}") String dbPath,
            MemoryConfig memoryConfig) {
        this.dbPath = dbPath;
        this.memoryConfig = memoryConfig;
    }

    @PostConstruct
    public void init() {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            connection = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
            jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, false));
            setupFtsTables();
        } catch (Exception e) {
            logger.error("Failed to initialize DuckDB Knowledge Base", e);
        }
    }

    private void setupFtsTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS repository_memories (" +
                "memory_id VARCHAR PRIMARY KEY, repo_path VARCHAR NOT NULL, finding_fingerprint VARCHAR, rule_id VARCHAR, "
                +
                "category VARCHAR, vulnerability_type VARCHAR, language VARCHAR, framework VARCHAR, file_pattern VARCHAR, "
                +
                "title VARCHAR, summary VARCHAR, root_cause VARCHAR, remediation_pattern VARCHAR, search_text VARCHAR, "
                +
                "confidence DOUBLE NOT NULL, source_run_id VARCHAR NOT NULL, approved BOOLEAN NOT NULL, usage_count INTEGER NOT NULL, "
                +
                "created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS global_remediation_patterns (" +
                "pattern_id VARCHAR PRIMARY KEY, rule_id VARCHAR NOT NULL, category VARCHAR, " +
                "positive_pattern VARCHAR, negative_pattern VARCHAR, confidence DOUBLE NOT NULL, " +
                "source_run_id VARCHAR, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS memory_fts_state (" +
                "state_id INTEGER PRIMARY KEY, source_version BIGINT NOT NULL DEFAULT 0, " +
                "indexed_version BIGINT NOT NULL DEFAULT 0, last_result VARCHAR, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute(
                "INSERT OR IGNORE INTO memory_fts_state (state_id, source_version, indexed_version, updated_at) " +
                        "VALUES (1, 0, 0, CURRENT_TIMESTAMP)");

        logger.info("DuckDB FTS tables initialized.");
    }

    @PreDestroy
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("Error closing DuckDB connection", e);
            }
        }
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * Search for approved repository memories using FTS and metadata ranking.
     */
    public List<RepositoryMemory> searchRepositoryMemories(String repoPath, Vulnerability vulnerability) {
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();

        if (ftsAvailable && query != null && !query.isBlank()) {
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
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();

        if (ftsAvailable && query != null && !query.isBlank()) {
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
        String sql = "SELECT rm.*, fts_main_repository_memories.match_bm25(rm.memory_id, ?) AS score " +
                "FROM repository_memories rm WHERE rm.repo_path = ? AND rm.approved = ? " +
                "AND fts_main_repository_memories.match_bm25(rm.memory_id, ?) IS NOT NULL " +
                "ORDER BY score DESC LIMIT ?";
        try {
            jdbcTemplate.query(sql, rs -> {
                String memoryId = rs.getString("memory_id");
                if (!candidates.containsKey(memoryId)) {
                    candidates.put(memoryId, mapRepositoryMemory(rs, rs.getDouble("score")));
                }
            }, query, repoPath, approved, query, memoryConfig.getCandidateLimit());
        } catch (Exception e) {
            logger.debug("FTS query failed, falling back to metadata search: {}", e.getMessage());
        }
    }

    private void loadMetadataCandidates(String repoPath, Vulnerability vulnerability, boolean approved,
            Map<String, RepositoryMemory> candidates) {
        String sql = "SELECT *, 0.0 AS score FROM repository_memories " +
                "WHERE repo_path = ? AND approved = ? AND (" +
                "finding_fingerprint = ? OR rule_id = ? OR vulnerability_type = ? OR language = ?) " +
                "ORDER BY confidence DESC, updated_at DESC LIMIT ?";
        try {
            jdbcTemplate.query(sql, rs -> {
                String memoryId = rs.getString("memory_id");
                if (!candidates.containsKey(memoryId)) {
                    candidates.put(memoryId, mapRepositoryMemory(rs, 0.0));
                }
            }, repoPath, approved,
                    vulnerability.getFindingFingerprint(),
                    vulnerability.getRuleId(),
                    vulnerability.getVulnType(),
                    vulnerability.getLanguage(),
                    memoryConfig.getCandidateLimit());
        } catch (Exception e) {
            logger.debug("Metadata candidate load failed: {}", e.getMessage());
        }
    }

    private RepositoryMemory mapRepositoryMemory(ResultSet rs, double score) throws SQLException {
        return new RepositoryMemory(
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
                jdbcTemplate.update("UPDATE repository_memories SET usage_count=usage_count+1 WHERE memory_id=?",
                        memory.memoryId());
            }
        } catch (Exception e) {
            logger.debug("Could not update memory usage counters", e);
        }
    }

    /**
     * Rebuild the DuckDB FTS index for repository_memories.
     * Uses a lock to prevent concurrent rebuilds.
     */
    public void ensureFtsIndex() {
        if (!memoryConfig.isFtsEnabled() || jdbcTemplate == null) {
            return;
        }
        ftsLock.lock();
        try {
            loadFtsExtension();
            List<Map<String, Object>> state = jdbcTemplate.queryForList(
                    "SELECT source_version, indexed_version FROM memory_fts_state WHERE state_id=1");
            if (!state.isEmpty()) {
                long sourceVersion = ((Number) state.get(0).get("source_version")).longValue();
                long indexedVersion = ((Number) state.get(0).get("indexed_version")).longValue();
                if (sourceVersion != indexedVersion || !ftsAvailable) {
                    jdbcTemplate.execute(
                            "PRAGMA create_fts_index('repository_memories','memory_id','title','summary'," +
                                    "'root_cause','remediation_pattern','search_text',stemmer='porter',stopwords='english',"
                                    +
                                    "strip_accents=1,lower=1,overwrite=1)");
                    jdbcTemplate.update(
                            "UPDATE memory_fts_state SET indexed_version=?, last_result='OK', " +
                                    "updated_at=CURRENT_TIMESTAMP WHERE state_id=1",
                            sourceVersion);
                }
            }
            ftsAvailable = true;
            logger.info("DuckDB FTS memory index is ready");
        } catch (Exception e) {
            ftsAvailable = false;
            logger.warn("DuckDB FTS unavailable; metadata retrieval remains active: {}", e.getMessage());
            updateFtsFailure(e.getMessage());
        } finally {
            ftsLock.unlock();
        }
    }

    private void loadFtsExtension() {
        try {
            jdbcTemplate.execute("LOAD fts");
            return;
        } catch (Exception loadError) {
            logger.debug("DuckDB FTS was not installed yet: {}", loadError.getMessage());
        }
        try {
            jdbcTemplate.execute("INSTALL fts");
            jdbcTemplate.execute("LOAD fts");
        } catch (Exception e) {
            throw new RuntimeException("Could not install/load DuckDB FTS extension", e);
        }
    }

    private void updateFtsFailure(String message) {
        try {
            String truncated = message != null && message.length() > 500 ? message.substring(0, 500) : message;
            jdbcTemplate.update(
                    "UPDATE memory_fts_state SET last_result=?, updated_at=CURRENT_TIMESTAMP WHERE state_id=1",
                    "FAILED: " + truncated);
        } catch (Exception ignored) {
        }
    }

    public boolean isFtsAvailable() {
        return ftsAvailable;
    }

    private String normalizePath(String path) {
        if (path == null)
            return "";
        return Path.of(path).normalize().toString();
    }

    public void syncRepositoryMemory(RepositoryMemory memory) {
        if (jdbcTemplate == null)
            return;
        jdbcTemplate.update("DELETE FROM repository_memories WHERE memory_id = ?", memory.memoryId());
        jdbcTemplate.update("INSERT INTO repository_memories (" +
                "memory_id, repo_path, finding_fingerprint, rule_id, category, vulnerability_type, " +
                "language, framework, file_pattern, title, summary, root_cause, remediation_pattern, " +
                "search_text, confidence, source_run_id, approved, usage_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                memory.memoryId(), normalizePath(memory.repoPath()), memory.findingFingerprint(), memory.ruleId(),
                memory.category(), memory.vulnerabilityType(), memory.language(), memory.framework(),
                memory.filePattern(), memory.title(), memory.summary(), memory.rootCause(),
                memory.remediationPattern(), memory.searchText(), memory.confidence(), memory.sourceRunId(),
                true, memory.usageCount(),
                memory.updatedAt() != null ? memory.updatedAt() : LocalDateTime.now(),
                memory.updatedAt() != null ? memory.updatedAt() : LocalDateTime.now());
    }
}
