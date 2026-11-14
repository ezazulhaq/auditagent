package com.cb.auditagent.service.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Applies database performance migrations for PostgreSQL:
 * - Generates stored 'search_vector' tsvector column on repository_memories.
 * - Creates GIN index on search_vector for fast sub-millisecond FTS retrieval.
 */
@Service
public class PostgresSchemaMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(PostgresSchemaMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresSchemaMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            DataSource ds = jdbcTemplate.getDataSource();
            if (ds == null) {
                return;
            }
            try (Connection conn = ds.getConnection()) {
                String dbName = conn.getMetaData().getDatabaseProductName();
                if ("PostgreSQL".equalsIgnoreCase(dbName)) {
                    applyPostgresMigrations();
                } else {
                    logger.debug("Skipping PostgreSQL specific migrations for database product: {}", dbName);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed checking database product or applying schema migrations: {}", e.getMessage());
        }
    }

    public void applyPostgresMigrations() {
        logger.info("Applying PostgreSQL performance hardening migrations (Phase 2)...");
        try {
            // Add stored search_vector generated column if not present
            jdbcTemplate.execute(
                "ALTER TABLE repository_memories " +
                "ADD COLUMN IF NOT EXISTS search_vector tsvector " +
                "GENERATED ALWAYS AS (" +
                "    to_tsvector('english', " +
                "        coalesce(title, '') || ' ' || " +
                "        coalesce(summary, '') || ' ' || " +
                "        coalesce(root_cause, '') || ' ' || " +
                "        coalesce(remediation_pattern, '') || ' ' || " +
                "        coalesce(search_text, '')" +
                "    )" +
                ") STORED"
            );

            // Add GIN index on search_vector
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_repo_memories_search_vector " +
                "ON repository_memories USING GIN(search_vector)"
            );

            logger.info("Successfully verified and applied PostgreSQL Phase 2 migrations (search_vector + GIN index)");
        } catch (Exception e) {
            logger.warn("Could not apply PostgreSQL search_vector migration: {}", e.getMessage());
        }
    }
}
