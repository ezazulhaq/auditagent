package com.cb.auditagent.service.data;

import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.repository.ReportRepository;
import com.cb.auditagent.repository.VulnerabilityEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScanDataServiceBatchUpsertTest {

    @MockitoBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @MockitoBean
    private dev.langchain4j.model.chat.ChatModel agentModel;

    @MockitoBean
    private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @Autowired
    private ScanDataService scanDataService;

    @Autowired
    private VulnerabilityEntityRepository vulnerabilityEntityRepository;

    @Autowired
    private ReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        scanDataService.setBatchSize(100);
    }

    @Test
    void testBatchUpsertOneThousandFindingsPerformanceAndNormalization() {
        String repoPath = "bench/batch-upsert-repo";
        int count = 1000;
        List<Vulnerability> findings = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Vulnerability v = new Vulnerability(
                    "VULN-BATCH-" + i,
                    "src/com/example/File" + (i % 50) + ".java",
                    10 + (i % 200),
                    "String query = \"SELECT * FROM users WHERE id = \" + id_" + i + ";",
                    i % 3 == 0 ? Severity.HIGH : (i % 2 == 0 ? Severity.MEDIUM : Severity.LOW),
                    "sql-injection",
                    "Unsanitized user input concatenated into SQL query #" + i,
                    "java"
            );
            v.setRuleId("rules.security.sqli." + (i % 20));
            findings.add(v);
        }

        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("BatchBench");
        metadata.setTotalFilesScanned(50);
        metadata.setTotalLocScanned(10000);

        long start = System.currentTimeMillis();
        scanDataService.saveReport(repoPath, "# Batch Report", "<p>Batch Report</p>", findings, metadata);
        long elapsed = System.currentTimeMillis() - start;

        // Verify batch performance is rapid without N+1 query overhead
        assertTrue(elapsed < 2000, "1,000 findings should persist rapidly via batchUpdate. Actual time: " + elapsed + "ms");

        // Verify normalized vulnerabilities rows are present
        assertEquals(count, vulnerabilityEntityRepository.findByRepoPath(repoPath).size());

        // Verify JSON bloat elimination: reports.findings_json should be null
        com.cb.auditagent.entity.Report entityReport = reportRepository.findById(repoPath).orElseThrow();
        assertNull(entityReport.getFindingsJson(), "Duplicated relational findings_json must be null to eliminate bloat");

        // Verify getReport loads normalized findings transparently
        Optional<Report> loadedReportOpt = scanDataService.getReport(repoPath);
        assertTrue(loadedReportOpt.isPresent());
        Report loadedReport = loadedReportOpt.get();
        assertEquals(count, loadedReport.getFindings().size());
        assertEquals(repoPath, loadedReport.getRepoPath());

        // Verify individual finding lookup
        Vulnerability firstFinding = loadedReport.getFindings().get(0);
        Optional<Vulnerability> fetched = scanDataService.getVulnerabilityById(firstFinding.getId());
        assertTrue(fetched.isPresent());
        assertEquals(VulnerabilityStatus.DETECTED, fetched.get().getStatus());

        // Test re-scan: update status on one finding, ensure existing IDs and status are preserved on subsequent scan
        scanDataService.updateVulnerabilityStatus(firstFinding.getId(), VulnerabilityStatus.FIXED, "PreparedStatement fix");

        // Re-run saveReport with the original findings list
        scanDataService.saveReport(repoPath, "# Updated Report", "<p>Updated</p>", findings, metadata);

        Vulnerability preserved = scanDataService.getVulnerabilityById(firstFinding.getId()).orElseThrow();
        assertEquals(VulnerabilityStatus.FIXED, preserved.getStatus(), "Existing finding status must be preserved across rescans");
        assertEquals("PreparedStatement fix", preserved.getProposedFix(), "Existing proposed fix must be preserved across rescans");
    }
}
