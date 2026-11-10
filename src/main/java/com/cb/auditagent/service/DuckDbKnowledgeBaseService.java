package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.repository.RepositoryMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * @deprecated Deprecated in favor of {@link PostgresKnowledgeBaseService}.
 * Retained for backwards compatibility with legacy callers; all memory retrieval
 * operations now execute against PostgreSQL.
 */
@Deprecated
@Service
public class DuckDbKnowledgeBaseService extends PostgresKnowledgeBaseService {

    @Autowired
    public DuckDbKnowledgeBaseService(JdbcTemplate jdbcTemplate,
                                      RepositoryMemoryRepository memoryRepository,
                                      MemoryConfig memoryConfig) {
        super(jdbcTemplate, memoryRepository, memoryConfig);
    }
}
