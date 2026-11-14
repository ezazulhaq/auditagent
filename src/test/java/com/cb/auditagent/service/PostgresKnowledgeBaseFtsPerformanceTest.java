package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.repository.RepositoryMemoryRepository;
import com.cb.auditagent.service.data.PostgresSchemaMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PostgresKnowledgeBaseFtsPerformanceTest {

    @MockitoBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @MockitoBean
    private dev.langchain4j.model.chat.ChatModel agentModel;

    @Autowired
    private PostgresKnowledgeBaseService postgresKnowledgeBaseService;

    @Autowired
    private RepositoryMemoryRepository repositoryMemoryRepository;

    @Autowired
    private MemoryConfig memoryConfig;

    @Autowired
    private PostgresSchemaMigrationService schemaMigrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PostgreSQL schema migration service executes idempotently without throwing exceptions")
    void testSchemaMigrationIdempotency() {
        assertDoesNotThrow(() -> schemaMigrationService.applyPostgresMigrations());
    }

    @Test
    @DisplayName("Repository memory retrieval ranks exact fingerprint matches above broad concept matches")
    void testCandidateRankingAndFallback() {
        String repoPath = "github.com/org/repo-fts-test";

        // Memory 1: Exact finding fingerprint match
        com.cb.auditagent.entity.RepositoryMemory exact = new com.cb.auditagent.entity.RepositoryMemory();
        exact.setMemoryId("mem-exact-001");
        exact.setRepoPath(repoPath);
        exact.setFindingFingerprint("fp-sql-exact-1234");
        exact.setRuleId("java.spring.security.audit.sqli");
        exact.setCategory("SQL_INJECTION");
        exact.setVulnerabilityType("SQL Injection");
        exact.setLanguage("java");
        exact.setFramework("spring");
        exact.setTitle("Fix SQL Injection with NamedParameterJdbcTemplate");
        exact.setSummary("Replaced raw string concatenation with parameterized prepared statements");
        exact.setRootCause("Raw string concatenation in dynamic SQL construction");
        exact.setRemediationPattern("Use jdbcTemplate with parameters");
        exact.setSearchText("SQL injection prepared statement named parameter");
        exact.setConfidence(0.95);
        exact.setSourceRunId("run-101");
        exact.setApproved(true);
        exact.setUsageCount(5);
        exact.setCreatedAt(LocalDateTime.now());
        exact.setUpdatedAt(LocalDateTime.now());
        repositoryMemoryRepository.save(exact);

        // Memory 2: Broad concept match (same category, different fingerprint)
        com.cb.auditagent.entity.RepositoryMemory broad = new com.cb.auditagent.entity.RepositoryMemory();
        broad.setMemoryId("mem-broad-002");
        broad.setRepoPath(repoPath);
        broad.setFindingFingerprint("fp-sql-other-9999");
        broad.setRuleId("java.spring.security.audit.sqli-other");
        broad.setCategory("SQL_INJECTION");
        broad.setVulnerabilityType("SQL Injection");
        broad.setLanguage("java");
        broad.setFramework("spring");
        broad.setTitle("Fix SQL Injection in Batch Query");
        broad.setSummary("Used parameterized criteria query");
        broad.setRootCause("Unescaped parameters in dynamic query");
        broad.setRemediationPattern("Use parameter binding");
        broad.setSearchText("SQL injection criteria query");
        broad.setConfidence(0.80);
        broad.setSourceRunId("run-102");
        broad.setApproved(true);
        broad.setUsageCount(1);
        broad.setCreatedAt(LocalDateTime.now().minusDays(2));
        broad.setUpdatedAt(LocalDateTime.now().minusDays(2));
        repositoryMemoryRepository.save(broad);

        // Target vulnerability matching exact fingerprint
        Vulnerability target = new Vulnerability(
                "VULN-TARGET",
                "src/main/java/UserRepo.java",
                42,
                "String sql = \"SELECT * FROM users WHERE name = '\" + input + \"'\";",
                Severity.HIGH,
                "SQL Injection",
                "Unsanitized user input concatenated into SQL query",
                "java"
        );
        target.setRuleId("java.spring.security.audit.sqli");
        target.setFindingFingerprint("fp-sql-exact-1234");

        List<RepositoryMemory> results = postgresKnowledgeBaseService.searchRepositoryMemories(repoPath, target);

        assertNotNull(results, "Search results should not be null");
        assertFalse(results.isEmpty(), "Should find at least 1 memory candidate");
        assertEquals("mem-exact-001", results.getFirst().memoryId(), "Exact fingerprint match must outrank broad match");
    }

    @Test
    @DisplayName("Negative rejected memories can be retrieved for anti-pattern guidance")
    void testSearchRejectedMemories() {
        String repoPath = "github.com/org/repo-anti-pattern";

        com.cb.auditagent.entity.RepositoryMemory rejected = new com.cb.auditagent.entity.RepositoryMemory();
        rejected.setMemoryId("mem-rejected-001");
        rejected.setRepoPath(repoPath);
        rejected.setFindingFingerprint("fp-xss-rejected-5555");
        rejected.setRuleId("java.spring.security.audit.xss");
        rejected.setCategory("XSS");
        rejected.setVulnerabilityType("Cross-Site Scripting");
        rejected.setLanguage("java");
        rejected.setTitle("Ineffective HTML Escaping");
        rejected.setSummary("Custom regex replace failed to sanitize HTML attributes");
        rejected.setRootCause("Custom escaping bypassable by hex encoding");
        rejected.setRemediationPattern("Do not use custom regex for XSS mitigation");
        rejected.setSearchText("XSS cross site scripting regex replace");
        rejected.setConfidence(0.30);
        rejected.setSourceRunId("run-103");
        rejected.setApproved(false); // Rejected memory
        rejected.setUsageCount(0);
        rejected.setCreatedAt(LocalDateTime.now());
        rejected.setUpdatedAt(LocalDateTime.now());
        repositoryMemoryRepository.save(rejected);

        Vulnerability target = new Vulnerability(
                "VULN-XSS",
                "src/main/java/ViewController.java",
                88,
                "response.getWriter().write(input);",
                Severity.HIGH,
                "Cross-Site Scripting",
                "Raw reflection of user input",
                "java"
        );
        target.setRuleId("java.spring.security.audit.xss");
        target.setFindingFingerprint("fp-xss-rejected-5555");

        List<RepositoryMemory> rejectedMatches = postgresKnowledgeBaseService.searchRejectedMemories(repoPath, target);

        assertNotNull(rejectedMatches);
        assertFalse(rejectedMatches.isEmpty(), "Rejected memory candidate should be returned");
        assertEquals("mem-rejected-001", rejectedMatches.getFirst().memoryId());
    }
}
