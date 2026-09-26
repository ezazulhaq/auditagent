package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final int CURRENT_SCHEMA_VERSION = 7;
    private static final String UTC_NOW_SQL = "(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')";

    private final String dbPath;
    private final ObjectMapper objectMapper;
    private final MemoryConfig memoryConfig;
    private final ReentrantLock ftsLock = new ReentrantLock();
    private volatile boolean ftsAvailable;

    public DatabaseService(@Value("${auditagent.database.path:audit_reports.duckdb}") String dbPath,
            ObjectMapper objectMapper, MemoryConfig memoryConfig) {
        this.dbPath = dbPath;
        this.objectMapper = objectMapper;
        this.memoryConfig = memoryConfig;
    }

    @PostConstruct
    public void init() {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            backupBeforeMemoryMigration();
            migrate();
            markActiveRunsInterrupted();
            if (memoryConfig.isFtsEnabled()) {
                Thread.startVirtualThread(this::ensureFtsIndex);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize DuckDB persistence", e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:duckdb:" + dbPath);
    }

    public void withTransaction(java.util.function.Consumer<Connection> action) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.accept(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new IllegalStateException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    private void backupBeforeMemoryMigration() {
        if (dbPath == null || dbPath.isBlank() || dbPath.contains(":memory:"))
            return;
        try {
            Path source = Path.of(dbPath).toAbsolutePath().normalize();
            Path backup = Path.of(dbPath + ".pre-memory-v1.bak").toAbsolutePath().normalize();
            if (Files.isRegularFile(source) && !Files.exists(backup)) {
                Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
                logger.info("Created pre-memory migration backup at {}", backup);
            }
        } catch (Exception e) {
            logger.warn("Could not create pre-memory database backup: {}", e.getMessage());
        }
    }

    private void migrate() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TIMESTAMP NOT NULL)");
                createLegacyTables(stmt);
                migrateVulnerabilities(conn, stmt);
                createMemoryTables(stmt);
                recordSchemaVersion(conn, 1);
                createGitHubWorkflowTables(stmt);
                recordSchemaVersion(conn, 2);
                migrateGitHubWorkflowTables(stmt);
                recordSchemaVersion(conn, 3);
                createScanHistoryTables(stmt);
                recordSchemaVersion(conn, 4);
                createGraphTables(stmt);
                recordSchemaVersion(conn, 5);
                stmt.execute("DROP INDEX IF EXISTS idx_run_pub_repo_pr");
                recordSchemaVersion(conn, 6);
                createNotificationTables(stmt);
                recordSchemaVersion(conn, CURRENT_SCHEMA_VERSION);
                conn.commit();
                logger.info("DuckDB schema migrated to version {}", CURRENT_SCHEMA_VERSION);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void recordSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_version VALUES (?, CURRENT_TIMESTAMP) ON CONFLICT (version) DO NOTHING")) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    private void createNotificationTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS user_notifications (" +
                "id VARCHAR PRIMARY KEY, user_id VARCHAR NOT NULL, type VARCHAR NOT NULL, message TEXT NOT NULL, " +
                "is_read BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMP NOT NULL)");
    }

    private void createLegacyTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS reports (" +
                "repo_path VARCHAR PRIMARY KEY, md_report VARCHAR, html_report VARCHAR, findings VARCHAR, metadata VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS vulnerabilities (" +
                "id VARCHAR PRIMARY KEY, repo_path VARCHAR, file VARCHAR, type VARCHAR, status VARCHAR, " +
                "description VARCHAR, code_context VARCHAR, severity VARCHAR, line_number INTEGER, proposed_fix VARCHAR)");
    }

    private void migrateVulnerabilities(Connection conn, Statement stmt) throws SQLException {
        addColumnIfMissing(conn, stmt, "vulnerabilities", "rule_id", "VARCHAR");
        addColumnIfMissing(conn, stmt, "vulnerabilities", "language", "VARCHAR");
        addColumnIfMissing(conn, stmt, "vulnerabilities", "finding_fingerprint", "VARCHAR");
        // DuckDB 1.1.x cannot reliably update a row when the changed column is covered
        // by a secondary ART index; fingerprint lookups remain bounded by repository.
        stmt.execute("DROP INDEX IF EXISTS idx_vuln_repo_fingerprint");
    }

    private void addColumnIfMissing(Connection conn, Statement stmt, String table, String column, String type)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Table and column names cannot be parameterized in SQL.
                    // We validate them against a whitelist or ensure they are identifiers.
                    if (!table.matches("^[a-zA-Z0-9_]+$") || !column.matches("^[a-zA-Z0-9_]+$")
                            || !type.matches("^[a-zA-Z0-9_ ]+$")) {
                        throw new IllegalArgumentException("Invalid table, column, or type name");
                    }
                    stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                }
            }
        }
    }

    private void createGraphTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS graph_checkpoints (" +
                "checkpoint_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, thread_id VARCHAR NOT NULL, " +
                "parent_checkpoint_id VARCHAR, node_name VARCHAR NOT NULL, state_json VARCHAR, " +
                "interrupt_reason VARCHAR, created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS graph_node_events (" +
                "event_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, checkpoint_id VARCHAR NOT NULL, " +
                "node_name VARCHAR NOT NULL, status VARCHAR NOT NULL, duration_ms BIGINT, " +
                "detail_json VARCHAR, created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS subagent_state (" +
                "state_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, parent_checkpoint_id VARCHAR NOT NULL, " +
                "child_agent VARCHAR NOT NULL, sequence_number INTEGER NOT NULL, context_json VARCHAR, " +
                "result_json VARCHAR, status VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP)");
    }

    private void createMemoryTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS memory_threads (" +
                "thread_id VARCHAR PRIMARY KEY, client_id VARCHAR NOT NULL, repo_path VARCHAR NOT NULL, " +
                "summary VARCHAR, state VARCHAR NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP NOT NULL, last_accessed_at TIMESTAMP NOT NULL)");
        stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_thread_client_repo ON memory_threads(client_id, repo_path)");

        stmt.execute("CREATE TABLE IF NOT EXISTS memory_messages (" +
                "message_id VARCHAR PRIMARY KEY, thread_id VARCHAR NOT NULL, run_id VARCHAR, sequence_number BIGINT NOT NULL, "
                +
                "role VARCHAR NOT NULL, message_type VARCHAR NOT NULL, content_redacted VARCHAR, content_hash VARCHAR, "
                +
                "token_estimate INTEGER, metadata_json VARCHAR, created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP)");
        stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_message_sequence ON memory_messages(thread_id, sequence_number)");

        stmt.execute("CREATE TABLE IF NOT EXISTS agent_runs (" +
                "run_id VARCHAR, thread_id VARCHAR NOT NULL, vulnerability_id VARCHAR NOT NULL, " +
                "finding_fingerprint VARCHAR, repo_path VARCHAR NOT NULL, phase VARCHAR NOT NULL, status VARCHAR NOT NULL, "
                +
                "iteration INTEGER NOT NULL, retry_count INTEGER NOT NULL, max_iterations INTEGER NOT NULL, " +
                "patch_applied BOOLEAN NOT NULL, compile_passed BOOLEAN NOT NULL, rescan_passed BOOLEAN NOT NULL, " +
                "tests_passed BOOLEAN NOT NULL, checkpoint_json VARCHAR, final_summary VARCHAR, error_code VARCHAR, " +
                "error_detail VARCHAR, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, completed_at TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_agent_runs_thread_status ON agent_runs(thread_id, status)");

        stmt.execute("CREATE TABLE IF NOT EXISTS agent_steps (" +
                "step_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, sequence_number BIGINT NOT NULL, iteration INTEGER NOT NULL, "
                +
                "action VARCHAR NOT NULL, tool_name VARCHAR, arguments_redacted VARCHAR, result_redacted VARCHAR, " +
                "result_status VARCHAR, duration_ms BIGINT, idempotency_key VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL, "
                +
                "expires_at TIMESTAMP)");
        stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_step_idempotency ON agent_steps(run_id, idempotency_key)");

        stmt.execute("CREATE TABLE IF NOT EXISTS verification_results (" +
                "verification_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, kind VARCHAR NOT NULL, status VARCHAR NOT NULL, "
                +
                "evidence_redacted VARCHAR, exit_code INTEGER, created_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS run_changes (" +
                "change_id VARCHAR PRIMARY KEY, run_id VARCHAR NOT NULL, file_path VARCHAR NOT NULL, before_hash VARCHAR, "
                +
                "after_hash VARCHAR, backup_path VARCHAR, state VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL)");

        stmt.execute("CREATE TABLE IF NOT EXISTS repository_memories (" +
                "memory_id VARCHAR PRIMARY KEY, repo_path VARCHAR NOT NULL, finding_fingerprint VARCHAR, rule_id VARCHAR, "
                +
                "category VARCHAR, vulnerability_type VARCHAR, language VARCHAR, framework VARCHAR, file_pattern VARCHAR, "
                +
                "title VARCHAR, summary VARCHAR, root_cause VARCHAR, remediation_pattern VARCHAR, search_text VARCHAR, "
                +
                "confidence DOUBLE NOT NULL, source_run_id VARCHAR NOT NULL, approved BOOLEAN NOT NULL, usage_count INTEGER NOT NULL, "
                +
                "created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS global_remediation_patterns (" +
                "pattern_id VARCHAR PRIMARY KEY, rule_id VARCHAR NOT NULL, category VARCHAR, " +
                "positive_pattern VARCHAR, negative_pattern VARCHAR, confidence DOUBLE NOT NULL, " +
                "source_run_id VARCHAR, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_global_pattern_rule " +
                "ON global_remediation_patterns(rule_id)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_repository_memory_fingerprint " +
                "ON repository_memories(repo_path, finding_fingerprint)");
        stmt.execute("CREATE TABLE IF NOT EXISTS memory_fts_state (" +
                "state_id INTEGER PRIMARY KEY, source_version BIGINT NOT NULL, indexed_version BIGINT NOT NULL, " +
                "last_result VARCHAR, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("INSERT INTO memory_fts_state VALUES (1, 0, 0, 'NOT_BUILT', CURRENT_TIMESTAMP) " +
                "ON CONFLICT (state_id) DO NOTHING");
    }

    private void createGitHubWorkflowTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS app_users (" +
                "user_id VARCHAR PRIMARY KEY, github_user_id BIGINT UNIQUE NOT NULL, login VARCHAR NOT NULL, " +
                "display_name VARCHAR, avatar_url VARCHAR, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS github_authorizations (" +
                "user_id VARCHAR PRIMARY KEY, access_token_encrypted VARCHAR NOT NULL, access_expires_at TIMESTAMP, " +
                "refresh_token_encrypted VARCHAR, refresh_expires_at TIMESTAMP, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS user_sessions (" +
                "session_hash VARCHAR PRIMARY KEY, user_id VARCHAR NOT NULL, csrf_token VARCHAR NOT NULL, " +
                "created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, last_seen_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON user_sessions(user_id)");
        stmt.execute("CREATE TABLE IF NOT EXISTS oauth_states (" +
                "state_hash VARCHAR PRIMARY KEY, verifier_encrypted VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL, " +
                "expires_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS managed_repositories (" +
                "repository_id BIGINT PRIMARY KEY, installation_id BIGINT NOT NULL, owner_login VARCHAR NOT NULL, " +
                "repo_name VARCHAR NOT NULL, full_name VARCHAR NOT NULL, clone_url VARCHAR NOT NULL, " +
                "default_branch VARCHAR NOT NULL, is_private BOOLEAN NOT NULL, permission VARCHAR, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS scan_snapshots (" +
                "snapshot_id VARCHAR PRIMARY KEY, repository_id BIGINT NOT NULL, branch VARCHAR NOT NULL, " +
                "base_sha VARCHAR NOT NULL, report_key VARCHAR NOT NULL, workspace_path VARCHAR NOT NULL, " +
                "created_by_user_id VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_scan_snapshot_repo_branch " +
                "ON scan_snapshots(repository_id,branch,created_at)");
        stmt.execute("CREATE TABLE IF NOT EXISTS run_publications (" +
                "run_id VARCHAR PRIMARY KEY, user_id VARCHAR NOT NULL, repository_id BIGINT NOT NULL, installation_id BIGINT NOT NULL, "
                +
                "report_key VARCHAR NOT NULL, base_branch VARCHAR NOT NULL, base_sha VARCHAR NOT NULL, " +
                "workspace_path VARCHAR NOT NULL, branch_name VARCHAR NOT NULL, approval_digest VARCHAR, approved_by VARCHAR, "
                +
                "approved_at TIMESTAMP, commit_sha VARCHAR, push_status VARCHAR NOT NULL, pr_number INTEGER, pr_url VARCHAR, "
                +
                "pr_state VARCHAR, publish_error VARCHAR, updated_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS webhook_deliveries (" +
                "delivery_id VARCHAR PRIMARY KEY, event_type VARCHAR NOT NULL, received_at TIMESTAMP NOT NULL)");
    }

    private void migrateGitHubWorkflowTables(Statement stmt) throws SQLException {
        // DuckDB 1.1.x cannot reliably update pr_number while a secondary ART index
        // covers it.
        // Webhook lookups remain correct and bounded by repository even without this
        // optional index.
        stmt.execute("DROP INDEX IF EXISTS idx_run_publication_pr");
    }

    private void createScanHistoryTables(Statement stmt) throws SQLException {
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS token_usage (id VARCHAR PRIMARY KEY, user_id VARCHAR NOT NULL, repo_path VARCHAR, vulnerability_id VARCHAR, thread_id VARCHAR, run_id VARCHAR, model_name VARCHAR NOT NULL, tokens INTEGER NOT NULL, created_at TIMESTAMP NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS scan_history_stats (" +
                "history_id VARCHAR PRIMARY KEY, repository_id BIGINT NOT NULL, branch VARCHAR NOT NULL, " +
                "base_sha VARCHAR NOT NULL, created_at TIMESTAMP NOT NULL, total_findings INTEGER NOT NULL, " +
                "high_severity INTEGER NOT NULL, medium_severity INTEGER NOT NULL, low_severity INTEGER NOT NULL, " +
                "info_severity INTEGER NOT NULL)");
        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_scan_history_repo_branch ON scan_history_stats(repository_id, branch)");
    }

    public String normalizePath(String path) {
        if (path == null || path.isBlank())
            return "";
        if (path.startsWith("github:"))
            return path;
        try {
            return Path.of(path).toFile().getCanonicalPath();
        } catch (Exception e) {
            return Path.of(path).toAbsolutePath().normalize().toString();
        }
    }

    public synchronized void saveReport(String repoPath, String mdReport, String htmlReport,
            List<Vulnerability> findings, ScanMetadata metadata) {
        String normalizedRepoPath = normalizePath(repoPath);
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Vulnerability v : findings)
                    upsertVulnerability(conn, normalizedRepoPath, v);
                String findingsJson = objectMapper.writeValueAsString(findings);
                String metadataJson = objectMapper.writeValueAsString(metadata);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reports VALUES (?, ?, ?, ?, ?) ON CONFLICT (repo_path) DO UPDATE SET " +
                                "md_report=EXCLUDED.md_report, html_report=EXCLUDED.html_report, " +
                                "findings=EXCLUDED.findings, metadata=EXCLUDED.metadata")) {
                    ps.setString(1, normalizedRepoPath);
                    ps.setString(2, mdReport);
                    ps.setString(3, htmlReport);
                    ps.setString(4, findingsJson);
                    ps.setString(5, metadataJson);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error("Error saving report for {}", normalizedRepoPath, e);
            throw new IllegalStateException("Could not save report", e);
        }
    }

    public synchronized void saveScanHistory(ScanHistoryRecord record) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO scan_history_stats VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, record.historyId());
            ps.setLong(2, record.repositoryId());
            ps.setString(3, record.branch());
            ps.setString(4, record.baseSha());
            ps.setTimestamp(5, Timestamp.valueOf(record.createdAt()));
            ps.setInt(6, record.totalFindings());
            ps.setInt(7, record.highSeverity());
            ps.setInt(8, record.mediumSeverity());
            ps.setInt(9, record.lowSeverity());
            ps.setInt(10, record.infoSeverity());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not persist scan history record for repository {}", record.repositoryId(), e);
        }
    }

    public synchronized List<ScanHistoryRecord> getScanHistory(long repositoryId, String branch) {
        List<ScanHistoryRecord> history = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT history_id, repository_id, branch, base_sha, created_at, total_findings, " +
                                "high_severity, medium_severity, low_severity, info_severity " +
                                "FROM scan_history_stats WHERE repository_id=? AND branch=? ORDER BY created_at ASC")) {
            ps.setLong(1, repositoryId);
            ps.setString(2, branch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new ScanHistoryRecord(
                            rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            toLocalDateTime(rs.getTimestamp(5)), rs.getInt(6), rs.getInt(7),
                            rs.getInt(8), rs.getInt(9), rs.getInt(10)));
                }
            }
        } catch (SQLException e) {
            logger.error("Could not fetch scan history for repository {}", repositoryId, e);
        }
        return history;
    }

    private void upsertVulnerability(Connection conn, String repoPath, Vulnerability v) throws SQLException {
        String fingerprint = Optional.ofNullable(v.getFindingFingerprint())
                .filter(s -> !s.isBlank())
                .orElseGet(() -> FindingFingerprint.create(v.getRuleId(), v.getFilePath(), v.getCodeSnippet()));
        v.setFindingFingerprint(fingerprint);
        boolean reused = false;
        boolean reusedLegacyFinding = false;
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id, status, proposed_fix FROM vulnerabilities WHERE repo_path=? AND finding_fingerprint=? LIMIT 1")) {
            find.setString(1, repoPath);
            find.setString(2, fingerprint);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    reuseFindingIdentity(v, rs);
                    reused = true;
                }
            }
        }
        if (!reused) {
            try (PreparedStatement legacy = conn.prepareStatement(
                    "SELECT id,status,proposed_fix FROM vulnerabilities WHERE repo_path=? " +
                            "AND finding_fingerprint IS NULL AND file=? AND type=? AND code_context=? LIMIT 1")) {
                legacy.setString(1, repoPath);
                legacy.setString(2, v.getFilePath());
                legacy.setString(3, v.getVulnType());
                legacy.setString(4, v.getCodeSnippet());
                try (ResultSet rs = legacy.executeQuery()) {
                    if (rs.next()) {
                        reuseFindingIdentity(v, rs);
                        reusedLegacyFinding = true;
                        reused = true;
                    }
                }
            }
        }
        if (reused) {
            String updateSql = "UPDATE vulnerabilities SET file=?,type=?,status=?,description=?,code_context=?," +
                    "severity=?,line_number=?,proposed_fix=?,rule_id=?,language=?" +
                    (reusedLegacyFinding ? ",finding_fingerprint=?" : "") + " WHERE id=?";
            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, v.getFilePath());
                update.setString(2, v.getVulnType());
                update.setString(3, Optional.ofNullable(v.getStatus()).orElse(VulnerabilityStatus.DETECTED).name());
                update.setString(4, v.getDescription());
                update.setString(5, v.getCodeSnippet());
                update.setString(6, Optional.ofNullable(v.getSeverity()).orElse(Severity.MEDIUM).name());
                update.setInt(7, v.getLineNumber());
                update.setString(8, v.getProposedFix());
                update.setString(9, v.getRuleId());
                update.setString(10, v.getLanguage());
                int idIndex = 11;
                if (reusedLegacyFinding)
                    update.setString(idIndex++, fingerprint);
                update.setString(idIndex, v.getId());
                update.executeUpdate();
            }
            return;
        }
        String sql = "INSERT INTO vulnerabilities (id,repo_path,file,type,status,description,code_context,severity," +
                "line_number,proposed_fix,rule_id,language,finding_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getId());
            ps.setString(2, repoPath);
            ps.setString(3, v.getFilePath());
            ps.setString(4, v.getVulnType());
            ps.setString(5, Optional.ofNullable(v.getStatus()).orElse(VulnerabilityStatus.DETECTED).name());
            ps.setString(6, v.getDescription());
            ps.setString(7, v.getCodeSnippet());
            ps.setString(8, Optional.ofNullable(v.getSeverity()).orElse(Severity.MEDIUM).name());
            ps.setInt(9, v.getLineNumber());
            ps.setString(10, v.getProposedFix());
            ps.setString(11, v.getRuleId());
            ps.setString(12, v.getLanguage());
            ps.setString(13, fingerprint);
            ps.executeUpdate();
        }
    }

    private void reuseFindingIdentity(Vulnerability vulnerability, ResultSet existing) throws SQLException {
        vulnerability.setId(existing.getString(1));
        try {
            vulnerability.setStatus(VulnerabilityStatus.valueOf(existing.getString(2)));
        } catch (Exception ignored) {
            vulnerability.setStatus(VulnerabilityStatus.DETECTED);
        }
        if (vulnerability.getProposedFix() == null)
            vulnerability.setProposedFix(existing.getString(3));
    }

    public synchronized Optional<Report> getReport(String repoPath) {
        String normalized = normalizePath(repoPath);
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT md_report,html_report,findings,metadata FROM reports WHERE repo_path=?")) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<Vulnerability> findings = objectMapper.readValue(rs.getString(3), new TypeReference<>() {
                    });
                    for (int i = 0; i < findings.size(); i++) {
                        int index = i;
                        getVulnerabilityById(findings.get(i).getId(), normalized).ifPresent(liveV -> {
                            findings.get(index).setStatus(liveV.getStatus());
                            findings.get(index).setProposedFix(liveV.getProposedFix());
                        });
                    }
                    return Optional.of(new Report(normalized, rs.getString(1), rs.getString(2), findings,
                            objectMapper.readValue(rs.getString(4), ScanMetadata.class)));
                }
            }
        } catch (Exception e) {
            logger.error("Error reading report for {}", normalized, e);
        }
        return Optional.empty();
    }

    public synchronized Optional<Vulnerability> getVulnerabilityById(String vulnId) {
        return getVulnerabilityById(vulnId, null);
    }

    public synchronized Optional<Vulnerability> getVulnerabilityById(String vulnId, String repoPath) {
        String sql = "SELECT id,file,type,status,description,code_context,severity,line_number,proposed_fix," +
                "rule_id,language,finding_fingerprint FROM vulnerabilities WHERE id=?" +
                (repoPath == null ? "" : " AND repo_path=?");
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vulnId);
            if (repoPath != null)
                ps.setString(2, normalizePath(repoPath));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapVulnerability(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load vulnerability", e);
        }
        return Optional.empty();
    }

    private Vulnerability mapVulnerability(ResultSet rs) throws SQLException {
        Vulnerability v = new Vulnerability();
        v.setId(rs.getString(1));
        v.setFilePath(rs.getString(2));
        v.setVulnType(rs.getString(3));
        try {
            v.setStatus(VulnerabilityStatus.valueOf(rs.getString(4)));
        } catch (Exception e) {
            v.setStatus(VulnerabilityStatus.DETECTED);
        }
        v.setDescription(rs.getString(5));
        v.setCodeSnippet(rs.getString(6));
        try {
            v.setSeverity(Severity.valueOf(rs.getString(7)));
        } catch (Exception e) {
            v.setSeverity(Severity.MEDIUM);
        }
        v.setLineNumber(rs.getInt(8));
        v.setProposedFix(rs.getString(9));
        v.setRuleId(rs.getString(10));
        v.setLanguage(rs.getString(11));
        v.setFindingFingerprint(rs.getString(12));
        return v;
    }

    public synchronized void updateVulnerabilityStatus(String vulnId, VulnerabilityStatus status, String proposedFix) {
        try (Connection conn = getConnection()) {
            updateVulnerabilityStatusTx(conn, vulnId, status, proposedFix);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update vulnerability status", e);
        }
    }

    public void updateVulnerabilityStatusTx(Connection conn, String vulnId, VulnerabilityStatus status,
            String proposedFix) throws SQLException {
        try (PreparedStatement ps = conn
                .prepareStatement("UPDATE vulnerabilities SET status=?, proposed_fix=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setString(2, proposedFix);
            ps.setString(3, vulnId);
            ps.executeUpdate();
        }
    }

    public synchronized String getOrCreateMemoryThread(String clientId, String repoPath) {
        String normalizedRepo = normalizePath(repoPath);
        String safeClient = clientId == null || clientId.isBlank() ? "local-default" : clientId;
        try (Connection conn = getConnection()) {
            try (PreparedStatement find = conn.prepareStatement(
                    "SELECT thread_id FROM memory_threads WHERE client_id=? AND repo_path=?")) {
                find.setString(1, safeClient);
                find.setString(2, normalizedRepo);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        String id = rs.getString(1);
                        try (PreparedStatement touch = conn.prepareStatement(
                                "UPDATE memory_threads SET last_accessed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE thread_id=?")) {
                            touch.setString(1, id);
                            touch.executeUpdate();
                        }
                        return id;
                    }
                }
            }
            String id = UUID.randomUUID().toString();
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO memory_threads VALUES (?,?,?,NULL,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                insert.setString(1, id);
                insert.setString(2, safeClient);
                insert.setString(3, normalizedRepo);
                insert.executeUpdate();
            }
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create memory thread", e);
        }
    }

    public synchronized boolean memoryThreadExists(String threadId, String repoPath) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM memory_threads WHERE thread_id=? AND repo_path=?")) {
            ps.setString(1, threadId);
            ps.setString(2, normalizePath(repoPath));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void ensureMemoryThread(String threadId, String clientId, String repoPath) {
        String normalizedRepo = normalizePath(repoPath != null ? repoPath : "");
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO memory_threads VALUES (?,?,?,NULL,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                                +
                                "ON CONFLICT (thread_id) DO UPDATE SET last_accessed_at=EXCLUDED.last_accessed_at,updated_at=EXCLUDED.updated_at")) {
            ps.setString(1, threadId);
            ps.setString(2, clientId == null ? "local-default" : clientId);
            ps.setString(3, normalizedRepo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not ensure memory thread: " + e.getMessage(), e);
        }

        if (!normalizedRepo.isEmpty()) {
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE memory_threads SET repo_path=? WHERE thread_id=? AND repo_path!=?")) {
                ps.setString(1, normalizedRepo);
                ps.setString(2, threadId);
                ps.setString(3, normalizedRepo);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warn("Could not update repo_path for thread {}", threadId, e);
            }
        }
    }

    public synchronized MemoryMessageRecord appendMemoryMessage(String threadId, String runId, String role,
            String messageType, String content, String metadataJson) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(memoryConfig.getRetentionDays());
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                long sequence;
                try (PreparedStatement seq = conn.prepareStatement(
                        "SELECT COALESCE(MAX(sequence_number),0)+1 FROM memory_messages WHERE thread_id=?")) {
                    seq.setString(1, threadId);
                    try (ResultSet rs = seq.executeQuery()) {
                        rs.next();
                        sequence = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO memory_messages VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, id);
                    ps.setString(2, threadId);
                    ps.setString(3, runId);
                    ps.setLong(4, sequence);
                    ps.setString(5, role);
                    ps.setString(6, messageType);
                    ps.setString(7, content);
                    ps.setString(8, sha256(content));
                    ps.setInt(9, estimateTokens(content));
                    ps.setString(10, metadataJson);
                    ps.setTimestamp(11, Timestamp.valueOf(now));
                    ps.setTimestamp(12, Timestamp.valueOf(expires));
                    ps.executeUpdate();
                }
                try (PreparedStatement touch = conn.prepareStatement(
                        "UPDATE memory_threads SET updated_at=CURRENT_TIMESTAMP,last_accessed_at=CURRENT_TIMESTAMP WHERE thread_id=?")) {
                    touch.setString(1, threadId);
                    touch.executeUpdate();
                }
                conn.commit();
                return new MemoryMessageRecord(id, threadId, runId, sequence, role, messageType, content, metadataJson,
                        now);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist memory message", e);
        }
    }

    public synchronized List<MemoryMessageRecord> getMemoryMessages(String threadId) {
        return getMemoryMessages(threadId, null, false);
    }

    public synchronized List<MemoryMessageRecord> getMemoryMessages(String threadId, String runId,
            boolean includeGeneralChat) {
        List<MemoryMessageRecord> messages = new ArrayList<>();
        String runFilter = runId == null
                ? " AND run_id IS NULL"
                : includeGeneralChat ? " AND (run_id=? OR run_id IS NULL)" : " AND run_id=?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT message_id,thread_id,run_id,sequence_number,role,message_type,content_redacted," +
                                "metadata_json,created_at FROM memory_messages WHERE thread_id=? " +
                                "AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)" + runFilter
                                + " ORDER BY sequence_number")) {
            ps.setString(1, threadId);
            if (runId != null)
                ps.setString(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    messages.add(new MemoryMessageRecord(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getString(8), toLocalDateTime(rs.getTimestamp(9))));
            }
        } catch (SQLException e) {
            logger.error("Could not load memory thread {}", threadId, e);
        }
        return messages;
    }

    public synchronized void deleteMemoryThread(String threadId) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement runs = conn.prepareStatement("SELECT run_id FROM agent_runs WHERE thread_id=?")) {
                runs.setString(1, threadId);
                List<String> runIds = new ArrayList<>();
                try (ResultSet rs = runs.executeQuery()) {
                    while (rs.next())
                        runIds.add(rs.getString(1));
                }
                for (String runId : runIds)
                    deleteRunDetail(conn, runId);
                executeDelete(conn, "DELETE FROM agent_runs WHERE thread_id=?", threadId);
                executeDelete(conn, "DELETE FROM memory_messages WHERE thread_id=?", threadId);
                executeDelete(conn, "DELETE FROM memory_threads WHERE thread_id=?", threadId);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not forget memory thread", e);
        }
    }

    private void deleteRunDetail(Connection conn, String runId) throws SQLException {
        executeDelete(conn, "DELETE FROM agent_steps WHERE run_id=?", runId);
        executeDelete(conn, "DELETE FROM verification_results WHERE run_id=?", runId);
        executeDelete(conn, "DELETE FROM run_changes WHERE run_id=?", runId);
    }

    private void executeDelete(Connection conn, String sql, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.executeUpdate();
        }
    }

    public synchronized AgentRunRecord createAgentRun(String threadId, Vulnerability vulnerability,
            String repoPath, int maxIterations) {
        String runId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String normalizedRepo = normalizePath(repoPath);
        String fingerprint = Optional.ofNullable(vulnerability.getFindingFingerprint())
                .orElseGet(() -> FindingFingerprint.create(vulnerability.getRuleId(), vulnerability.getFilePath(),
                        vulnerability.getCodeSnippet()));
        try (Connection conn = getConnection()) {
            try (PreparedStatement active = conn.prepareStatement(
                    "SELECT run_id FROM agent_runs WHERE (thread_id=? OR repo_path=?) " +
                            "AND status IN ('ACTIVE','INTERRUPTED','AWAITING_APPROVAL','PUBLISHING','PUBLISH_FAILED','CONFLICTED') LIMIT 1")) {
                active.setString(1, threadId);
                active.setString(2, normalizedRepo);
                try (ResultSet rs = active.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalStateException(
                                "A remediation run is already active or recoverable: " + rs.getString(1));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO agent_runs VALUES (?,?,?,?,?,'PLANNING','ACTIVE',0,0,?,FALSE,FALSE,FALSE,FALSE," +
                            "NULL,NULL,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL)")) {
                ps.setString(1, runId);
                ps.setString(2, threadId);
                ps.setString(3, vulnerability.getId());
                ps.setString(4, fingerprint);
                ps.setString(5, normalizedRepo);
                ps.setInt(6, maxIterations);
                ps.executeUpdate();
            }
            return new AgentRunRecord(runId, threadId, vulnerability.getId(), fingerprint, normalizedRepo,
                    AgentPhase.PLANNING, AgentRunStatus.ACTIVE, 0, 0, maxIterations, false, false,
                    false, false, null, null, null, null, now);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create agent run", e);
        }
    }

    public synchronized AgentRunRecord createManagedAgentRun(String threadId, Vulnerability vulnerability,
            String workspacePath, int maxIterations) {
        String runId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String normalizedWorkspace = normalizePath(workspacePath);
        String fingerprint = Optional.ofNullable(vulnerability.getFindingFingerprint())
                .orElseGet(() -> FindingFingerprint.create(vulnerability.getRuleId(),
                        vulnerability.getFilePath(), vulnerability.getCodeSnippet()));
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO agent_runs VALUES (?,?,?,?,?,'PREPARING_WORKSPACE','ACTIVE',0,0,?,FALSE,FALSE,FALSE,FALSE,"
                                +
                                "NULL,NULL,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL)")) {
            ps.setString(1, runId);
            ps.setString(2, threadId);
            ps.setString(3, vulnerability.getId());
            ps.setString(4, fingerprint);
            ps.setString(5, normalizedWorkspace);
            ps.setInt(6, maxIterations);
            ps.executeUpdate();
            return new AgentRunRecord(runId, threadId, vulnerability.getId(), fingerprint, normalizedWorkspace,
                    AgentPhase.PREPARING_WORKSPACE, AgentRunStatus.ACTIVE, 0, 0, maxIterations,
                    false, false, false, false, null, null, null, null, now);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create managed agent run", e);
        }
    }

    public synchronized void updateAgentRun(String runId, AgentPhase phase, AgentRunStatus status,
            int iteration, int retryCount, boolean patchApplied,
            boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail) {
        try (Connection conn = getConnection()) {
            updateAgentRunTx(conn, runId, phase, status, iteration, retryCount, patchApplied, compilePassed,
                    rescanPassed, testsPassed, checkpointJson, finalSummary, errorCode, errorDetail);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not checkpoint agent run", e);
        }
    }

    public void updateAgentRunTx(Connection conn, String runId, AgentPhase phase, AgentRunStatus status,
            int iteration, int retryCount, boolean patchApplied,
            boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            String checkpointJson, String finalSummary, String errorCode, String errorDetail) throws SQLException {
        String sql = "UPDATE agent_runs SET phase=?,status=?,iteration=?,retry_count=?,patch_applied=?," +
                "compile_passed=?,rescan_passed=?,tests_passed=?,checkpoint_json=?,final_summary=?,error_code=?," +
                "error_detail=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ? IN " +
                "('APPROVED','REJECTED','FAILED','PR_MERGED','PR_CLOSED','CONFLICTED','DISCARDED') " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END WHERE run_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phase.name());
            ps.setString(2, status.name());
            ps.setInt(3, iteration);
            ps.setInt(4, retryCount);
            ps.setBoolean(5, patchApplied);
            ps.setBoolean(6, compilePassed);
            ps.setBoolean(7, rescanPassed);
            ps.setBoolean(8, testsPassed);
            ps.setString(9, checkpointJson);
            ps.setString(10, finalSummary);
            ps.setString(11, errorCode);
            ps.setString(12, errorDetail);
            ps.setString(13, status.name());
            ps.setString(14, runId);
            ps.executeUpdate();
        }
    }

    public synchronized Optional<AgentRunRecord> getAgentRun(String runId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT run_id,thread_id,vulnerability_id,finding_fingerprint,repo_path,phase,status,iteration,"
                                +
                                "retry_count,max_iterations,patch_applied,compile_passed,rescan_passed,tests_passed," +
                                "checkpoint_json,final_summary,error_code,error_detail,updated_at FROM agent_runs WHERE run_id=?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapAgentRun(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load agent run", e);
        }
        return Optional.empty();
    }

    public synchronized Optional<AgentRunRecord> findRecoverableRun(String threadId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT run_id,thread_id,vulnerability_id,finding_fingerprint,repo_path,phase,status,iteration,"
                                +
                                "retry_count,max_iterations,patch_applied,compile_passed,rescan_passed,tests_passed," +
                                "checkpoint_json,final_summary,error_code,error_detail,updated_at FROM agent_runs " +
                                "WHERE thread_id=? AND status IN ('INTERRUPTED','AWAITING_APPROVAL','PUBLISH_FAILED','PR_OPEN','CONFLICTED') "
                                +
                                "ORDER BY updated_at DESC LIMIT 1")) {
            ps.setString(1, threadId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapAgentRun(rs));
            }
        } catch (SQLException e) {
            logger.error("Could not load recoverable run for {}", threadId, e);
        }
        return Optional.empty();
    }

    private AgentRunRecord mapAgentRun(ResultSet rs) throws SQLException {
        return new AgentRunRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                AgentPhase.valueOf(rs.getString(6)), AgentRunStatus.valueOf(rs.getString(7)), rs.getInt(8),
                rs.getInt(9),
                rs.getInt(10), rs.getBoolean(11), rs.getBoolean(12), rs.getBoolean(13), rs.getBoolean(14),
                rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18),
                toLocalDateTime(rs.getTimestamp(19)));
    }

    private synchronized void markActiveRunsInterrupted() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE agent_runs SET phase='INTERRUPTED',status='INTERRUPTED'," +
                    "updated_at=CURRENT_TIMESTAMP WHERE status IN ('ACTIVE','PUBLISHING')");
            stmt.executeUpdate("UPDATE graph_checkpoints SET interrupt_reason='SERVER_RESTART' " +
                    "WHERE run_id IN (SELECT run_id FROM agent_runs WHERE status='INTERRUPTED') " +
                    "AND interrupt_reason IS NULL " +
                    "AND checkpoint_id IN (SELECT checkpoint_id FROM graph_checkpoints gc2 " +
                    "WHERE gc2.run_id = graph_checkpoints.run_id " +
                    "ORDER BY created_at DESC LIMIT 1)");
        } catch (SQLException e) {
            logger.warn("Could not mark interrupted runs: {}", e.getMessage());
        }
    }

    public synchronized boolean appendAgentStep(String runId, int iteration, String action, String toolName,
            String arguments, String result, String resultStatus,
            long durationMs, String idempotencyKey) {
        String sql = "INSERT INTO agent_steps VALUES (?,?,(SELECT COALESCE(MAX(sequence_number),0)+1 FROM agent_steps WHERE run_id=?),"
                +
                "?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?) ON CONFLICT (run_id,idempotency_key) DO NOTHING";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, runId);
            ps.setString(3, runId);
            ps.setInt(4, iteration);
            ps.setString(5, action);
            ps.setString(6, toolName);
            ps.setString(7, arguments);
            ps.setString(8, result);
            ps.setString(9, resultStatus);
            ps.setLong(10, durationMs);
            ps.setString(11, idempotencyKey);
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now().plusDays(memoryConfig.getRetentionDays())));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist agent step", e);
        }
    }

    public synchronized boolean hasCompletedStep(String runId, String idempotencyKey) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM agent_steps WHERE run_id=? AND idempotency_key=? AND result_status='SUCCESS'")) {
            ps.setString(1, runId);
            ps.setString(2, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void addVerificationResult(String runId, String kind, String status,
            String evidence, Integer exitCode) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO verification_results VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, runId);
            ps.setString(3, kind);
            ps.setString(4, status);
            ps.setString(5, evidence);
            if (exitCode == null)
                ps.setNull(6, Types.INTEGER);
            else
                ps.setInt(6, exitCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist verification result", e);
        }
    }

    public synchronized void addRunChange(String runId, String filePath, String beforeHash,
            String afterHash, String backupPath) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO run_changes VALUES (?,?,?,?,?,?,'APPLIED',CURRENT_TIMESTAMP)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, runId);
            ps.setString(3, filePath);
            ps.setString(4, beforeHash);
            ps.setString(5, afterHash);
            ps.setString(6, backupPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist run change", e);
        }
    }

    public synchronized List<RunChangeRecord> getRunChanges(String runId) {
        List<RunChangeRecord> changes = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT change_id,run_id,file_path,before_hash,after_hash,backup_path,state " +
                                "FROM run_changes WHERE run_id=? ORDER BY created_at DESC")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    changes.add(new RunChangeRecord(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
        } catch (SQLException e) {
            logger.error("Could not load changes for run {}", runId, e);
        }
        return changes;
    }

    public synchronized void markRunChangeState(String changeId, String state) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE run_changes SET state=? WHERE change_id=?")) {
            ps.setString(1, state);
            ps.setString(2, changeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not update change {}", changeId, e);
        }
    }

    public synchronized void saveApprovedRepositoryMemory(String repoPath, Vulnerability vulnerability,
            String runId, String summary) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveApprovedRepositoryMemoryTx(conn, repoPath, vulnerability, runId, summary);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist approved repository memory", e);
        }
        rebuildFtsAsync();
    }

    public void saveApprovedRepositoryMemoryTx(Connection conn, String repoPath, Vulnerability vulnerability,
            String runId, String summary) throws SQLException {
        SecurityConceptCatalog.Concept concept = SecurityConceptCatalog.describe(vulnerability);
        String normalizedRepo = normalizePath(repoPath);
        String fingerprint = Optional.ofNullable(vulnerability.getFindingFingerprint())
                .orElseGet(() -> FindingFingerprint.create(vulnerability.getRuleId(), vulnerability.getFilePath(),
                        vulnerability.getCodeSnippet()));
        String title = Optional.ofNullable(vulnerability.getVulnType()).orElse("Security remediation");
        String rootCause = Optional.ofNullable(vulnerability.getDescription()).orElse("");
        String remediation = Optional.ofNullable(summary).orElse("");
        String searchText = SecurityConceptCatalog.buildSearchText(vulnerability, summary, concept);
        String filePattern = SecurityConceptCatalog.filePattern(vulnerability.getFilePath());
        String sql = "INSERT INTO repository_memories VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,0," +
                "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (repo_path,finding_fingerprint) DO UPDATE SET " +
                "rule_id=EXCLUDED.rule_id,category=EXCLUDED.category,vulnerability_type=EXCLUDED.vulnerability_type," +
                "language=EXCLUDED.language,framework=EXCLUDED.framework,file_pattern=EXCLUDED.file_pattern," +
                "title=EXCLUDED.title,summary=EXCLUDED.summary,root_cause=EXCLUDED.root_cause," +
                "remediation_pattern=EXCLUDED.remediation_pattern,search_text=EXCLUDED.search_text," +
                "confidence=EXCLUDED.confidence,source_run_id=EXCLUDED.source_run_id,approved=TRUE,updated_at=now()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, normalizedRepo);
            ps.setString(3, fingerprint);
            ps.setString(4, vulnerability.getRuleId());
            ps.setString(5, concept.category());
            ps.setString(6, vulnerability.getVulnType());
            ps.setString(7, vulnerability.getLanguage());
            ps.setString(8, concept.framework());
            ps.setString(9, filePattern);
            ps.setString(10, title);
            ps.setString(11, summary);
            ps.setString(12, rootCause);
            ps.setString(13, remediation);
            ps.setString(14, searchText);
            ps.setDouble(15, 1.0);
            ps.setString(16, runId);
            ps.executeUpdate();
        }
        incrementFtsSourceVersion(conn);
    }

    public void saveRejectedRepositoryMemory(String runId, String reason) {
        try (Connection conn = getConnection()) {
            AgentRunRecord run = getAgentRun(runId).orElse(null);
            if (run == null)
                return;
            Vulnerability vulnerability = getVulnerabilityById(run.vulnerabilityId()).orElse(null);
            if (vulnerability == null)
                return;

            SecurityConceptCatalog.Concept concept = SecurityConceptCatalog.describe(vulnerability);
            String normalizedRepo = normalizePath(run.repoPath());
            String fingerprint = Optional.ofNullable(vulnerability.getFindingFingerprint())
                    .orElseGet(() -> FindingFingerprint.create(vulnerability.getRuleId(), vulnerability.getFilePath(),
                            vulnerability.getCodeSnippet()));
            String title = Optional.ofNullable(vulnerability.getVulnType()).orElse("Rejected remediation");
            String rootCause = Optional.ofNullable(vulnerability.getDescription()).orElse("");
            String remediation = Optional.ofNullable(vulnerability.getProposedFix()).orElse("");
            String searchText = SecurityConceptCatalog.buildSearchText(vulnerability, reason, concept);
            String filePattern = SecurityConceptCatalog.filePattern(vulnerability.getFilePath());
            String sql = "INSERT INTO repository_memories VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,0," +
                    "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (repo_path,finding_fingerprint) DO UPDATE SET " +
                    "summary=EXCLUDED.summary, remediation_pattern=EXCLUDED.remediation_pattern, approved=FALSE, updated_at=now()";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, normalizedRepo);
                ps.setString(3, fingerprint);
                ps.setString(4, vulnerability.getRuleId());
                ps.setString(5, concept.category());
                ps.setString(6, vulnerability.getVulnType());
                ps.setString(7, vulnerability.getLanguage());
                ps.setString(8, concept.framework());
                ps.setString(9, filePattern);
                ps.setString(10, title);
                ps.setString(11, reason != null ? reason : "No reason provided");
                ps.setString(12, rootCause);
                ps.setString(13, remediation);
                ps.setString(14, searchText);
                ps.setDouble(15, 1.0);
                ps.setString(16, runId);
                ps.executeUpdate();
            }
            incrementFtsSourceVersion(conn);
        } catch (SQLException e) {
            logger.error("Could not save rejected memory", e);
        }
    }

    public void lowerRepositoryMemoryConfidenceTx(Connection conn, String reportKey, Vulnerability vulnerability,
            String runId) throws SQLException {
        String fingerprint = Optional.ofNullable(vulnerability.getFindingFingerprint())
                .orElseGet(() -> FindingFingerprint.create(vulnerability.getRuleId(), vulnerability.getFilePath(),
                        vulnerability.getCodeSnippet()));
        String sql = "UPDATE repository_memories SET confidence = confidence * 0.5, updated_at=now() WHERE source_run_id = ? OR finding_fingerprint = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, fingerprint);
            ps.executeUpdate();
        }
    }

    public void saveGlobalPatternTx(Connection conn, String ruleId,
            String positivePattern, String negativePattern, String runId) throws SQLException {
        if (ruleId == null || ruleId.isBlank())
            return;
        // Accumulate patterns: append new content to existing rather than overwriting.
        // COALESCE(existing || '\n---\n' || new, new) keeps history; cap at 4000 chars
        // on read.
        String sql = "INSERT INTO global_remediation_patterns VALUES (?,?,NULL,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                +
                "ON CONFLICT (rule_id) DO UPDATE SET " +
                "positive_pattern=CASE WHEN EXCLUDED.positive_pattern IS NOT NULL THEN " +
                "  CASE WHEN global_remediation_patterns.positive_pattern IS NOT NULL THEN " +
                "    LEFT(global_remediation_patterns.positive_pattern || '\n---\n' || EXCLUDED.positive_pattern, 4000) "
                +
                "  ELSE EXCLUDED.positive_pattern END " +
                "ELSE global_remediation_patterns.positive_pattern END, " +
                "negative_pattern=CASE WHEN EXCLUDED.negative_pattern IS NOT NULL THEN " +
                "  CASE WHEN global_remediation_patterns.negative_pattern IS NOT NULL THEN " +
                "    LEFT(global_remediation_patterns.negative_pattern || '\n---\n' || EXCLUDED.negative_pattern, 4000) "
                +
                "  ELSE EXCLUDED.negative_pattern END " +
                "ELSE global_remediation_patterns.negative_pattern END, " +
                "confidence=EXCLUDED.confidence, source_run_id=EXCLUDED.source_run_id, updated_at=now()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, ruleId);
            ps.setString(3, positivePattern);
            ps.setString(4, negativePattern);
            ps.setDouble(5, 1.0);
            ps.setString(6, runId);
            ps.executeUpdate();
        }
    }

    public record GlobalPattern(String ruleId, String positivePattern, String negativePattern) {
    }

    public Optional<GlobalPattern> getGlobalPattern(String ruleId) {
        String sql = "SELECT positive_pattern, negative_pattern FROM global_remediation_patterns WHERE rule_id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new GlobalPattern(ruleId, rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to get global pattern for rule {}", ruleId, e);
        }
        return Optional.empty();
    }

    public synchronized List<RepositoryMemory> searchRepositoryMemories(String repoPath, Vulnerability vulnerability) {
        if (!memoryConfig.isEnabled())
            return List.of();
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();
        if (ftsAvailable && !query.isBlank())
            loadFtsCandidates(normalizedRepo, query, true, candidates);
        loadMetadataCandidates(normalizedRepo, vulnerability, true, candidates);
        List<RepositoryMemory> ranked = candidates.values().stream()
                .map(m -> withScore(m, calculateMemoryScore(m, vulnerability)))
                .sorted(Comparator.comparingDouble(RepositoryMemory::score).reversed())
                .limit(memoryConfig.getTopK()).toList();
        if (!ranked.isEmpty())
            incrementMemoryUsage(ranked);
        return ranked;
    }

    private void loadFtsCandidates(String repoPath, String query, boolean approved,
            Map<String, RepositoryMemory> target) {
        String sql = "SELECT * FROM (SELECT memory_id,repo_path,finding_fingerprint,rule_id,category,vulnerability_type,"
                +
                "language,framework,file_pattern,title,summary,root_cause,remediation_pattern,search_text,confidence," +
                "source_run_id,usage_count,updated_at,fts_main_repository_memories.match_bm25(memory_id,?) AS bm25 " +
                "FROM repository_memories WHERE approved=? AND repo_path=?) q WHERE bm25 IS NOT NULL " +
                "ORDER BY bm25 DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query);
            ps.setBoolean(2, approved);
            ps.setString(3, repoPath);
            ps.setInt(4, memoryConfig.getCandidateLimit());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RepositoryMemory memory = mapRepositoryMemory(rs, Math.max(0.0, rs.getDouble(19)));
                    target.put(memory.memoryId(), memory);
                }
            }
        } catch (SQLException e) {
            ftsAvailable = false;
            logger.warn("DuckDB FTS query failed; using metadata fallback: {}", e.getMessage());
        }
    }

    private void loadMetadataCandidates(String repoPath, Vulnerability vulnerability, boolean approved,
            Map<String, RepositoryMemory> target) {
        String sql = "SELECT memory_id,repo_path,finding_fingerprint,rule_id,category,vulnerability_type,language," +
                "framework,file_pattern,title,summary,root_cause,remediation_pattern,search_text,confidence," +
                "source_run_id,usage_count,updated_at FROM repository_memories WHERE approved=? AND repo_path=? " +
                "ORDER BY CASE WHEN finding_fingerprint=? THEN 0 WHEN rule_id=? THEN 1 WHEN category=? THEN 2 ELSE 3 END,"
                +
                "updated_at DESC LIMIT ?";
        SecurityConceptCatalog.Concept concept = SecurityConceptCatalog.describe(vulnerability);
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, approved);
            ps.setString(2, repoPath);
            ps.setString(3, vulnerability.getFindingFingerprint());
            ps.setString(4, vulnerability.getRuleId());
            ps.setString(5, concept.category());
            ps.setInt(6, memoryConfig.getCandidateLimit());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RepositoryMemory memory = mapRepositoryMemory(rs, 0.0);
                    target.putIfAbsent(memory.memoryId(), memory);
                }
            }
        } catch (SQLException e) {
            logger.error("Metadata memory retrieval failed", e);
        }
    }

    public synchronized List<RepositoryMemory> searchRejectedMemories(String repoPath, Vulnerability vulnerability) {
        if (!memoryConfig.isEnabled())
            return List.of();
        String normalizedRepo = normalizePath(repoPath);
        String query = SecurityConceptCatalog.buildQuery(vulnerability);
        Map<String, RepositoryMemory> candidates = new LinkedHashMap<>();
        if (ftsAvailable && !query.isBlank())
            loadFtsCandidates(normalizedRepo, query, false, candidates);
        loadMetadataCandidates(normalizedRepo, vulnerability, false, candidates);
        List<RepositoryMemory> ranked = candidates.values().stream()
                .map(m -> withScore(m, calculateMemoryScore(m, vulnerability)))
                .sorted(Comparator.comparingDouble(RepositoryMemory::score).reversed())
                .limit(memoryConfig.getTopK()).toList();
        if (!ranked.isEmpty())
            incrementMemoryUsage(ranked);
        return ranked;
    }

    private RepositoryMemory mapRepositoryMemory(ResultSet rs, double score) throws SQLException {
        return new RepositoryMemory(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getDouble(15), rs.getString(16), rs.getInt(17), toLocalDateTime(rs.getTimestamp(18)), score);
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
                : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(memory.updatedAt(), LocalDateTime.now()));
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
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE repository_memories SET usage_count=usage_count+1 WHERE memory_id=?")) {
            for (RepositoryMemory memory : memories) {
                ps.setString(1, memory.memoryId());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.debug("Could not update memory usage counters", e);
        }
    }

    public synchronized void deleteRepositoryMemories(String repoPath) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM repository_memories WHERE repo_path=?")) {
                ps.setString(1, normalizePath(repoPath));
                ps.executeUpdate();
            }
            incrementFtsSourceVersion(conn);
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not forget repository memory", e);
        }
        rebuildFtsAsync();
    }

    private void incrementFtsSourceVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE memory_fts_state SET source_version=source_version+1," +
                    "updated_at=CURRENT_TIMESTAMP WHERE state_id=1");
        }
    }

    private void rebuildFtsAsync() {
        if (memoryConfig.isFtsEnabled())
            Thread.startVirtualThread(this::ensureFtsIndex);
    }

    public void ensureFtsIndex() {
        if (!memoryConfig.isFtsEnabled())
            return;
        ftsLock.lock();
        try (Connection conn = getConnection()) {
            loadFtsExtension(conn);
            long sourceVersion;
            long indexedVersion;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT source_version,indexed_version FROM memory_fts_state WHERE state_id=1")) {
                rs.next();
                sourceVersion = rs.getLong(1);
                indexedVersion = rs.getLong(2);
            }
            if (sourceVersion != indexedVersion || !ftsAvailable) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA create_fts_index('repository_memories','memory_id','title','summary'," +
                            "'root_cause','remediation_pattern','search_text',stemmer='porter',stopwords='english'," +
                            "strip_accents=1,lower=1,overwrite=1)");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE memory_fts_state SET indexed_version=?,last_result='OK'," +
                                "updated_at=CURRENT_TIMESTAMP WHERE state_id=1")) {
                    ps.setLong(1, sourceVersion);
                    ps.executeUpdate();
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

    private void loadFtsExtension(Connection conn) throws SQLException {
        try (Statement load = conn.createStatement()) {
            load.execute("LOAD fts");
            return;
        } catch (SQLException loadError) {
            logger.debug("DuckDB FTS was not installed yet: {}", loadError.getMessage());
        }
        try (Statement install = conn.createStatement()) {
            install.execute("INSTALL fts");
        }
        try (Statement load = conn.createStatement()) {
            load.execute("LOAD fts");
        }
    }

    private synchronized void updateFtsFailure(String message) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE memory_fts_state SET last_result=?,updated_at=CURRENT_TIMESTAMP WHERE state_id=1")) {
            ps.setString(1, "FAILED: " + truncate(message, 500));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public boolean isFtsAvailable() {
        return ftsAvailable;
    }

    public synchronized String upsertGitHubUser(long githubUserId, String login, String displayName, String avatarUrl) {
        String userId = "github:" + githubUserId;
        String sql = "INSERT INTO app_users VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) " +
                "ON CONFLICT (github_user_id) DO UPDATE SET login=EXCLUDED.login,display_name=EXCLUDED.display_name," +
                "avatar_url=EXCLUDED.avatar_url,updated_at=EXCLUDED.updated_at";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, githubUserId);
            ps.setString(3, login);
            ps.setString(4, displayName);
            ps.setString(5, avatarUrl);
            ps.executeUpdate();
            return userId;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save GitHub user", e);
        }
    }

    public synchronized void saveGitHubAuthorization(GitHubAuthorization authorization) {
        String sql = "INSERT INTO github_authorizations VALUES (?,?,?,?,?,CURRENT_TIMESTAMP) " +
                "ON CONFLICT (user_id) DO UPDATE SET access_token_encrypted=EXCLUDED.access_token_encrypted," +
                "access_expires_at=EXCLUDED.access_expires_at,refresh_token_encrypted=EXCLUDED.refresh_token_encrypted,"
                +
                "refresh_expires_at=EXCLUDED.refresh_expires_at,updated_at=EXCLUDED.updated_at";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, authorization.userId());
            ps.setString(2, authorization.accessTokenEncrypted());
            setTimestamp(ps, 3, authorization.accessExpiresAt());
            ps.setString(4, authorization.refreshTokenEncrypted());
            setTimestamp(ps, 5, authorization.refreshExpiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save GitHub authorization", e);
        }
    }

    public synchronized Optional<GitHubAuthorization> getGitHubAuthorization(String userId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT user_id,access_token_encrypted,access_expires_at,refresh_token_encrypted,refresh_expires_at "
                                +
                                "FROM github_authorizations WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(new GitHubAuthorization(rs.getString(1), rs.getString(2),
                            toLocalDateTime(rs.getTimestamp(3)), rs.getString(4), toLocalDateTime(rs.getTimestamp(5))));
            }
        } catch (SQLException e) {
            logger.error("Could not load GitHub authorization", e);
        }
        return Optional.empty();
    }

    public synchronized void saveOAuthState(String stateHash, String verifierEncrypted, LocalDateTime expiresAt) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO oauth_states VALUES (?,?,CURRENT_TIMESTAMP,?)")) {
            ps.setString(1, stateHash);
            ps.setString(2, verifierEncrypted);
            setTimestamp(ps, 3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist OAuth state", e);
        }
    }

    public synchronized Optional<String> consumeOAuthState(String stateHash) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String verifier = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT verifier_encrypted FROM oauth_states WHERE state_hash=? AND expires_at>" + UTC_NOW_SQL)) {
                ps.setString(1, stateHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        verifier = rs.getString(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM oauth_states WHERE state_hash=?")) {
                ps.setString(1, stateHash);
                ps.executeUpdate();
            }
            conn.commit();
            return Optional.ofNullable(verifier);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not consume OAuth state", e);
        }
    }

    public synchronized void createUserSession(String sessionHash, String userId, String csrfToken,
            LocalDateTime expiresAt) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO user_sessions VALUES (?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)")) {
            ps.setString(1, sessionHash);
            ps.setString(2, userId);
            ps.setString(3, csrfToken);
            setTimestamp(ps, 4, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create user session", e);
        }
    }

    public synchronized Optional<AuthenticatedUser> findAuthenticatedUser(String sessionHash) {
        String sql = "SELECT u.user_id,u.github_user_id,u.login,u.display_name,u.avatar_url,s.csrf_token " +
                "FROM user_sessions s JOIN app_users u ON u.user_id=s.user_id " +
                "WHERE s.session_hash=? AND s.expires_at>" + UTC_NOW_SQL;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement touch = conn.prepareStatement(
                            "UPDATE user_sessions SET last_seen_at=CURRENT_TIMESTAMP WHERE session_hash=?")) {
                        touch.setString(1, sessionHash);
                        touch.executeUpdate();
                    }
                    return Optional.of(new AuthenticatedUser(rs.getString(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6)));
                }
            }
        } catch (SQLException e) {
            logger.error("Could not resolve user session", e);
        }
        return Optional.empty();
    }

    public synchronized void deleteUserSession(String sessionHash) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM user_sessions WHERE session_hash=?")) {
            ps.setString(1, sessionHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Could not delete user session: {}", e.getMessage());
        }
    }

    public synchronized void upsertManagedRepository(ManagedRepository repository) {
        String sql = "INSERT INTO managed_repositories VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) " +
                "ON CONFLICT (repository_id) DO UPDATE SET installation_id=EXCLUDED.installation_id," +
                "owner_login=EXCLUDED.owner_login,repo_name=EXCLUDED.repo_name,full_name=EXCLUDED.full_name," +
                "clone_url=EXCLUDED.clone_url,default_branch=EXCLUDED.default_branch,is_private=EXCLUDED.is_private," +
                "permission=EXCLUDED.permission,updated_at=EXCLUDED.updated_at";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, repository.repositoryId());
            ps.setLong(2, repository.installationId());
            ps.setString(3, repository.owner());
            ps.setString(4, repository.name());
            ps.setString(5, repository.fullName());
            ps.setString(6, repository.cloneUrl());
            ps.setString(7, repository.defaultBranch());
            ps.setBoolean(8, repository.privateRepository());
            ps.setString(9, repository.permission());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save managed repository", e);
        }
    }

    public synchronized Optional<ManagedRepository> getManagedRepository(long repositoryId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT repository_id,installation_id,owner_login,repo_name,full_name,clone_url,default_branch,"
                                +
                                "is_private,permission FROM managed_repositories WHERE repository_id=?")) {
            ps.setLong(1, repositoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(new ManagedRepository(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8),
                            rs.getString(9)));
            }
        } catch (SQLException e) {
            logger.error("Could not load managed repository", e);
        }
        return Optional.empty();
    }

    public synchronized void saveScanSnapshot(ScanSnapshot snapshot) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO scan_snapshots VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, snapshot.snapshotId());
            ps.setLong(2, snapshot.repositoryId());
            ps.setString(3, snapshot.branch());
            ps.setString(4, snapshot.baseSha());
            ps.setString(5, snapshot.reportKey());
            ps.setString(6, snapshot.workspacePath());
            ps.setString(7, snapshot.createdByUserId());
            setTimestamp(ps, 8, snapshot.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save scan snapshot", e);
        }
    }

    public synchronized Optional<ScanSnapshot> findLatestScanSnapshot(long repositoryId, String branch) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT snapshot_id,repository_id,branch,base_sha,report_key,workspace_path,created_by_user_id,created_at "
                                +
                                "FROM scan_snapshots WHERE repository_id=? AND branch=? ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, repositoryId);
            ps.setString(2, branch);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(new ScanSnapshot(rs.getString(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            toLocalDateTime(rs.getTimestamp(8))));
            }
        } catch (SQLException e) {
            logger.error("Could not load scan snapshot", e);
        }
        return Optional.empty();
    }

    private void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null)
            ps.setNull(index, Types.TIMESTAMP);
        else
            ps.setTimestamp(index, Timestamp.valueOf(value));
    }

    public synchronized void createRunPublication(RunPublicationRecord publication) {
        String sql = "INSERT INTO run_publications VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPublication(ps, publication);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create run publication", e);
        }
    }

    public synchronized void saveRunPublication(RunPublicationRecord publication) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveRunPublicationTx(conn, publication);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save run publication", e);
        }
    }

    public void saveRunPublicationTx(Connection conn, RunPublicationRecord publication) throws SQLException {
        String updateSql = "UPDATE run_publications SET approval_digest=?,approved_by=?,approved_at=?," +
                "commit_sha=?,push_status=?,pr_number=?,pr_url=?,pr_state=?,publish_error=?," +
                "updated_at=CURRENT_TIMESTAMP WHERE run_id=?";
        try (PreparedStatement update = conn.prepareStatement(updateSql)) {
            bindPublicationUpdate(update, publication);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO run_publications VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
                    bindPublication(insert, publication);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void bindPublicationUpdate(PreparedStatement ps, RunPublicationRecord p) throws SQLException {
        ps.setString(1, p.approvalDigest());
        ps.setString(2, p.approvedBy());
        setTimestamp(ps, 3, p.approvedAt());
        ps.setString(4, p.commitSha());
        ps.setString(5, p.pushStatus());
        if (p.pullRequestNumber() == null)
            ps.setNull(6, Types.INTEGER);
        else
            ps.setInt(6, p.pullRequestNumber());
        ps.setString(7, p.pullRequestUrl());
        ps.setString(8, p.pullRequestState());
        ps.setString(9, p.publishError());
        ps.setString(10, p.runId());
    }

    private void bindPublication(PreparedStatement ps, RunPublicationRecord p) throws SQLException {
        ps.setString(1, p.runId());
        ps.setString(2, p.userId());
        ps.setLong(3, p.repositoryId());
        ps.setLong(4, p.installationId());
        ps.setString(5, p.reportKey());
        ps.setString(6, p.baseBranch());
        ps.setString(7, p.baseSha());
        ps.setString(8, p.workspacePath());
        ps.setString(9, p.branchName());
        ps.setString(10, p.approvalDigest());
        ps.setString(11, p.approvedBy());
        setTimestamp(ps, 12, p.approvedAt());
        ps.setString(13, p.commitSha());
        ps.setString(14, p.pushStatus());
        if (p.pullRequestNumber() == null)
            ps.setNull(15, Types.INTEGER);
        else
            ps.setInt(15, p.pullRequestNumber());
        ps.setString(16, p.pullRequestUrl());
        ps.setString(17, p.pullRequestState());
        ps.setString(18, p.publishError());
    }

    public synchronized Optional<RunPublicationRecord> getRunPublication(String runId) {
        return queryPublication("WHERE run_id=?", ps -> ps.setString(1, runId));
    }

    public synchronized boolean hasActiveManagedRun(long repositoryId, String branch, String fingerprint) {
        String sql = "SELECT COUNT(*) FROM run_publications p JOIN agent_runs r ON r.run_id=p.run_id " +
                "WHERE p.repository_id=? AND p.base_branch=? AND r.finding_fingerprint=? AND r.status IN " +
                "('ACTIVE','INTERRUPTED','AWAITING_APPROVAL','PUBLISHING','PUBLISH_FAILED','PR_OPEN','CONFLICTED')";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, repositoryId);
            ps.setString(2, branch);
            ps.setString(3, fingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check active managed runs", e);
        }
    }

    public synchronized List<String> getActiveManagedRunIds(long repositoryId, String branch, String fingerprint) {
        String sql = "SELECT r.run_id FROM run_publications p JOIN agent_runs r ON r.run_id=p.run_id " +
                "WHERE p.repository_id=? AND p.base_branch=? AND r.finding_fingerprint=? AND r.status IN " +
                "('ACTIVE','INTERRUPTED','AWAITING_APPROVAL','PUBLISHING','PUBLISH_FAILED','PR_OPEN','CONFLICTED')";
        List<String> ids = new java.util.ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, repositoryId);
            ps.setString(2, branch);
            ps.setString(3, fingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not fetch active managed runs", e);
        }
        return ids;
    }

    public synchronized boolean memoryThreadBelongsTo(String threadId, String userId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM memory_threads WHERE thread_id=? AND client_id=?")) {
            ps.setString(1, threadId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized Optional<RunPublicationRecord> findRunPublication(long repositoryId, int prNumber) {
        return queryPublication("WHERE repository_id=? AND pr_number=?", ps -> {
            ps.setLong(1, repositoryId);
            ps.setInt(2, prNumber);
        });
    }

    public synchronized List<RunPublicationRecord> findStaleWorkspaces(List<String> terminalStatuses,
            java.time.LocalDateTime before) {
        String placeholders = String.join(",", java.util.Collections.nCopies(terminalStatuses.size(), "?"));
        String sql = "SELECT rp.run_id,rp.user_id,rp.repository_id,rp.installation_id,rp.report_key,rp.base_branch,rp.base_sha,"
                +
                "rp.workspace_path,rp.branch_name,rp.approval_digest,rp.approved_by,rp.approved_at,rp.commit_sha,rp.push_status,"
                +
                "rp.pr_number,rp.pr_url,rp.pr_state,rp.publish_error FROM run_publications rp " +
                "JOIN agent_runs ar ON rp.run_id = ar.run_id " +
                "WHERE ar.status IN (" + placeholders + ") AND ar.updated_at < ? AND rp.workspace_path IS NOT NULL";

        List<RunPublicationRecord> stale = new java.util.ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String status : terminalStatuses) {
                ps.setString(idx++, status);
            }
            ps.setTimestamp(idx, java.sql.Timestamp.valueOf(before));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stale.add(new RunPublicationRecord(rs.getString(1), rs.getString(2),
                            rs.getLong(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                            toLocalDateTime(rs.getTimestamp(12)), rs.getString(13), rs.getString(14),
                            (Integer) rs.getObject(15), rs.getString(16), rs.getString(17), rs.getString(18)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load stale workspaces", e);
        }
        return stale;
    }

    private Optional<RunPublicationRecord> queryPublication(String where, SqlBinder binder) {
        // Ensure 'where' is a safe, non-empty clause or empty string.
        // The caller must provide a parameterized WHERE clause (e.g., "WHERE run_id =
        // ?").
        if (where != null && !where.isBlank() && !where.trim().toUpperCase().startsWith("WHERE")) {
            throw new IllegalArgumentException("Invalid WHERE clause: must start with 'WHERE'");
        }
        String sql = "SELECT run_id,user_id,repository_id,installation_id,report_key,base_branch,base_sha," +
                "workspace_path,branch_name,approval_digest,approved_by,approved_at,commit_sha,push_status," +
                "pr_number,pr_url,pr_state,publish_error FROM run_publications " + (where == null ? "" : where);
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(new RunPublicationRecord(rs.getString(1), rs.getString(2),
                            rs.getLong(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                            toLocalDateTime(rs.getTimestamp(12)), rs.getString(13), rs.getString(14),
                            (Integer) rs.getObject(15), rs.getString(16), rs.getString(17), rs.getString(18)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load run publication", e);
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    public synchronized Map<String, String> getVerificationEvidence(String runId) {
        Map<String, String> evidence = new LinkedHashMap<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT kind,status,evidence_redacted FROM verification_results WHERE run_id=? ORDER BY created_at")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    evidence.put(rs.getString(1), rs.getString(2) +
                            (rs.getString(3) == null ? "" : ": " + truncate(rs.getString(3), 1000)));
            }
        } catch (SQLException e) {
            logger.error("Could not load verification evidence", e);
        }
        return evidence;
    }

    public synchronized boolean recordWebhookDelivery(String deliveryId, String eventType) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO webhook_deliveries VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT (delivery_id) DO NOTHING")) {
            ps.setString(1, deliveryId);
            ps.setString(2, eventType);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record webhook delivery", e);
        }
    }

    public synchronized void releaseWebhookDelivery(String deliveryId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM webhook_deliveries WHERE delivery_id=?")) {
            ps.setString(1, deliveryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not release failed webhook delivery", e);
        }
    }

    @Scheduled(cron = "0 15 2 * * *")
    public synchronized void deleteExpiredMemory() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            int messages = stmt.executeUpdate("DELETE FROM memory_messages WHERE expires_at<CURRENT_TIMESTAMP");
            int steps = stmt.executeUpdate("DELETE FROM agent_steps WHERE expires_at<CURRENT_TIMESTAMP");
            stmt.executeUpdate("DELETE FROM user_sessions WHERE expires_at<" + UTC_NOW_SQL);
            stmt.executeUpdate("DELETE FROM oauth_states WHERE expires_at<" + UTC_NOW_SQL);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE agent_runs SET error_detail=NULL WHERE updated_at<?")) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusDays(memoryConfig.getRetentionDays())));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE verification_results SET evidence_redacted=NULL WHERE created_at<?")) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusDays(memoryConfig.getRetentionDays())));
                ps.executeUpdate();
            }
            stmt.executeUpdate("UPDATE graph_checkpoints SET state_json=NULL " +
                    "WHERE expires_at < " + UTC_NOW_SQL + " AND state_json IS NOT NULL");
            stmt.executeUpdate("UPDATE graph_node_events SET detail_json=NULL " +
                    "WHERE expires_at < " + UTC_NOW_SQL + " AND detail_json IS NOT NULL");
            stmt.executeUpdate("UPDATE subagent_state SET context_json=NULL, result_json=NULL " +
                    "WHERE expires_at < " + UTC_NOW_SQL + " AND context_json IS NOT NULL");
            if (messages + steps > 0)
                logger.info("Expired {} memory detail row(s)", messages + steps);
        } catch (SQLException e) {
            logger.error("Memory retention cleanup failed", e);
        }
    }

    public synchronized void saveGraphCheckpoint(String checkpointId, String runId, String threadId,
            String parentId, String nodeName, String stateJson,
            String interruptReason) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO graph_checkpoints VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30' DAY)")) {
            ps.setString(1, checkpointId);
            ps.setString(2, runId);
            ps.setString(3, threadId);
            ps.setString(4, parentId);
            ps.setString(5, nodeName);
            ps.setString(6, stateJson);
            ps.setString(7, interruptReason);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save graph checkpoint", e);
        }
    }

    public synchronized String[] loadGraphCheckpoint(String checkpointId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT state_json, node_name FROM graph_checkpoints WHERE checkpoint_id=? AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) AND state_json IS NOT NULL")) {
            ps.setString(1, checkpointId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new String[] { rs.getString(1), rs.getString(2) };
            }
        } catch (SQLException e) {
            logger.error("Could not load graph checkpoint", e);
        }
        return null;
    }

    public synchronized String loadLatestGraphCheckpoint(String runId) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT state_json FROM graph_checkpoints WHERE run_id=? AND state_json IS NOT NULL ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.error("Could not load latest graph checkpoint", e);
        }
        return null;
    }

    public synchronized List<String> listGraphCheckpoints(String threadId) {
        List<String> result = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT checkpoint_id FROM graph_checkpoints WHERE thread_id=? ORDER BY created_at")) {
            ps.setString(1, threadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Could not list graph checkpoints", e);
        }
        return result;
    }

    public synchronized void updateAgentRunCheckpointJson(String runId, String json) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE agent_runs SET checkpoint_json=?, updated_at=CURRENT_TIMESTAMP WHERE run_id=?")) {
            ps.setString(1, json);
            ps.setString(2, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not update agent run checkpoint", e);
        }
    }

    public synchronized void saveNodeEvent(String eventId, String runId, String checkpointId,
            String nodeName, String status, Long durationMs, String detailJson) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO graph_node_events VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7' DAY)")) {
            ps.setString(1, eventId);
            ps.setString(2, runId);
            ps.setString(3, checkpointId);
            ps.setString(4, nodeName);
            ps.setString(5, status);
            ps.setObject(6, durationMs);
            ps.setString(7, detailJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save node event", e);
        }
    }

    public synchronized List<String> getNodeEvents(String runId) {
        List<String> result = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT detail_json FROM graph_node_events WHERE run_id=? ORDER BY created_at")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Could not load node events", e);
        }
        return result;
    }

    public synchronized void saveSubagentState(String stateId, String runId, String parentCheckpointId,
            String childAgent, int sequenceNumber, String contextJson,
            String resultJson, String status) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO subagent_state VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30' DAY)")) {
            ps.setString(1, stateId);
            ps.setString(2, runId);
            ps.setString(3, parentCheckpointId);
            ps.setString(4, childAgent);
            ps.setInt(5, sequenceNumber);
            ps.setString(6, contextJson);
            ps.setString(7, resultJson);
            ps.setString(8, status);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save subagent state", e);
        }
    }

    public synchronized void updateSubagentResult(String stateId, String resultJson, String status) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE subagent_state SET result_json=?, status=? WHERE state_id=?")) {
            ps.setString(1, resultJson);
            ps.setString(2, status);
            ps.setString(3, stateId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not update subagent result", e);
        }
    }

    public synchronized String getSubagentContext(String runId, String childAgent) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT context_json FROM subagent_state WHERE run_id=? AND child_agent=? AND status='PENDING' ORDER BY sequence_number DESC LIMIT 1")) {
            ps.setString(1, runId);
            ps.setString(2, childAgent);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.error("Could not load subagent context", e);
        }
        return null;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Optional.ofNullable(value).orElse("").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private int estimateTokens(String value) {
        return Math.max(1, Optional.ofNullable(value).orElse("").length() / 4);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max)
            return value;
        return value.substring(0, max);
    }

    public synchronized void saveTokenUsage(String threadId, String runId, String vulnerabilityId, String modelName,
            int tokens) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO token_usage (id, user_id, repo_path, vulnerability_id, thread_id, run_id, model_name, tokens, created_at) "
                                +
                                "SELECT ?, client_id, repo_path, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP " +
                                "FROM memory_threads WHERE thread_id=?")) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setString(2, vulnerabilityId);
            ps.setString(3, threadId);
            ps.setString(4, runId);
            ps.setString(5, modelName);
            ps.setInt(6, tokens);
            ps.setString(7, threadId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not save token usage", e);
        }
    }

    public synchronized List<Map<String, Object>> getTokenUsageForUser(String userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT repo_path, model_name, SUM(tokens) as total_tokens, COUNT(*) as call_count " +
                                "FROM token_usage WHERE user_id = ? GROUP BY repo_path, model_name ORDER BY total_tokens DESC")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("repoPath", rs.getString("repo_path"));
                    row.put("modelName", rs.getString("model_name"));
                    row.put("totalTokens", rs.getInt("total_tokens"));
                    row.put("callCount", rs.getInt("call_count"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("Could not fetch token usage", e);
        }
        return results;
    }

    public void saveNotification(Notification notification) {
        String sql = "INSERT INTO user_notifications (id, user_id, type, message, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, notification.getId());
            ps.setString(2, notification.getUserId());
            ps.setString(3, notification.getType());
            ps.setString(4, notification.getMessage());
            ps.setBoolean(5, notification.isRead());
            ps.setTimestamp(6, Timestamp.from(notification.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save notification: {}", notification.getId(), e);
        }
    }

    public List<Notification> getNotifications(String userId) {
        List<Notification> results = new ArrayList<>();
        String sql = "SELECT id, type, message, is_read, created_at FROM user_notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 100";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setId(rs.getString("id"));
                    n.setUserId(userId);
                    n.setType(rs.getString("type"));
                    n.setMessage(rs.getString("message"));
                    n.setRead(rs.getBoolean("is_read"));
                    n.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    results.add(n);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch notifications for user {}", userId, e);
        }
        return results;
    }

    public void markNotificationsRead(String userId) {
        String sql = "UPDATE user_notifications SET is_read = true WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to mark notifications as read for user {}", userId, e);
        }
    }
}
