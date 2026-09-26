package com.cb.auditagent.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.service.ReporterService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReporterServiceTest {
    private final ReporterService reporter = new ReporterService();

    @Test
    void generatesAReadableEnterprisePdfFromThePersistedScan() throws Exception {
        byte[] pdf = reporter.generatePdf(List.of(finding()), metadata(), "octo/payments", "main",
                "0123456789abcdef");

        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(pdf, 4));
        assertTrue(pdf.length > 1_000);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("ENTERPRISE SECURITY ASSESSMENT"));
            assertTrue(text.contains("octo/payments"));
            assertTrue(text.contains("VULN-000001"));
            assertTrue(text.contains("SQL Injection"));
        }
    }

    @Test
    void generatesPortableMarkdownWithFullFindingDetails() {
        Vulnerability finding = finding();
        finding.setDescription("Unsafe query | <script>alert(1)</script>");

        String markdown = reporter.generateMarkdown(List.of(finding), metadata(), "octo/payments", "main", "base-sha");

        assertTrue(markdown.startsWith("# AuditAgent Enterprise Security Assessment"));
        assertTrue(markdown.contains("| High | 1 |"));
        assertTrue(markdown.contains("Unsafe query | &lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(markdown.contains("| Branch | main |"));
        assertTrue(markdown.contains("| Scanned commit | `base-sha` |"));
        assertTrue(markdown.contains("~~~~java\nString sql"));
        assertTrue(markdown.contains("Confidential - repository-scoped"));
    }

    @Test
    void paginatesLargeFindingSetsWithoutDroppingTheFinalFinding() throws Exception {
        List<Vulnerability> findings = IntStream.rangeClosed(1, 30).mapToObj(index -> {
            Vulnerability finding = finding();
            finding.setId(String.format("VULN-%06d", index));
            finding.setDescription("Security evidence and remediation context for finding " + index + ". ".repeat(12));
            return finding;
        }).toList();

        byte[] pdf = reporter.generatePdf(findings, metadata(), "octo/payments", "release/2026", "base-sha");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 2);
            assertTrue(new PDFTextStripper().getText(document).contains("VULN-000030"));
        }
    }

    private ScanMetadata metadata() {
        ScanMetadata metadata = new ScanMetadata();
        metadata.setProjectName("octo/payments");
        metadata.setScannerName("semgrep");
        metadata.setStartTime("2026-07-31T01:00:00Z");
        metadata.setEndTime("2026-07-31T01:02:00Z");
        metadata.setScanDuration("120.00 seconds");
        metadata.setTotalFilesScanned(24);
        metadata.setTotalLocScanned(4_200);
        return metadata;
    }

    private Vulnerability finding() {
        Vulnerability finding = new Vulnerability("VULN-000001", "src/PaymentDao.java", 42,
                "String sql = selectFromPayment + id;", Severity.HIGH,
                "SQL Injection", "Untrusted input is concatenated into a database query.", "java");
        finding.setRuleId("java.lang.security.audit.sqli");
        return finding;
    }
}
