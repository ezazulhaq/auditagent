package com.cb.auditagent.service;

import com.cb.auditagent.client.jev.JevAnswer;
import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FindingTriageServiceTest {

    private JevClient mockJevClient;
    private JevProperties jevProperties;
    private SimpleMeterRegistry meterRegistry;
    private FindingTriageService triageService;

    @BeforeEach
    void setUp() {
        mockJevClient = Mockito.mock(JevClient.class);
        jevProperties = new JevProperties();
        jevProperties.setEnabled(true);
        jevProperties.getTriage().setEnabled(true);
        jevProperties.getTriage().setConfidenceThreshold(0.75);
        jevProperties.getTriage().setCriticalConfidenceThreshold(0.95);

        meterRegistry = new SimpleMeterRegistry();
        triageService = new FindingTriageService(mockJevClient, jevProperties, meterRegistry, null);
    }

    @Test
    void testTriageActionableVulnerability() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);

        JevResponse response = new JevResponse();
        response.setAnswers(Map.of("actionability", new JevAnswer("ACTIONABLE_VULNERABILITY", 0.92)));
        when(mockJevClient.execute(any(), eq("finding_triage"))).thenReturn(response);

        Vulnerability vuln = new Vulnerability("VULN-001", "src/main/App.java", 42, "query = \"SELECT \" + input;", Severity.HIGH, "SQL Injection", "Unsanitized SQL", "Java");

        List<Vulnerability> result = triageService.triageFindings(List.of(vuln), "/workspace");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getTriageActionable());
        assertEquals("ACTIONABLE_VULNERABILITY", result.get(0).getTriageClassification());
        assertEquals(0.92, result.get(0).getTriageConfidence());
    }

    @Test
    void testTriageFalsePositiveAboveThreshold() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);

        JevResponse response = new JevResponse();
        response.setAnswers(Map.of("actionability", new JevAnswer("FALSE_POSITIVE", 0.88)));
        when(mockJevClient.execute(any(), eq("finding_triage"))).thenReturn(response);

        Vulnerability vuln = new Vulnerability("VULN-002", "src/test/MockTest.java", 15, "mockPassword = \"admin\";", Severity.LOW, "Hardcoded Secret", "Hardcoded credential in test", "Java");

        List<Vulnerability> result = triageService.triageFindings(List.of(vuln), "/workspace");

        assertEquals(1, result.size());
        assertFalse(result.get(0).getTriageActionable());
        assertEquals("FALSE_POSITIVE", result.get(0).getTriageClassification());
        assertEquals(0.88, result.get(0).getTriageConfidence());
    }

    @Test
    void testTriageHighSeverityRequiresHigherConfidence() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);

        // 0.88 is above standard 0.75, but below high/critical threshold 0.95
        JevResponse response = new JevResponse();
        response.setAnswers(Map.of("actionability", new JevAnswer("FALSE_POSITIVE", 0.88)));
        when(mockJevClient.execute(any(), eq("finding_triage"))).thenReturn(response);

        Vulnerability vuln = new Vulnerability("VULN-003", "src/main/Auth.java", 100, "token = req.getHeader();", Severity.HIGH, "Authentication Bypass", "Potential auth bypass", "Java");

        List<Vulnerability> result = triageService.triageFindings(List.of(vuln), "/workspace");

        assertEquals(1, result.size());
        // Defaults to actionable because high severity requires >= 0.95 confidence
        assertTrue(result.get(0).getTriageActionable());
        assertEquals("ACTIONABLE_LOW_CONFIDENCE", result.get(0).getTriageClassification());
    }

    @Test
    void testTriageGracefulFallbackOnError() throws Exception {
        when(mockJevClient.isEnabled()).thenReturn(true);
        when(mockJevClient.execute(any(), eq("finding_triage"))).thenThrow(new RuntimeException("OpenRouter timeout"));

        Vulnerability vuln = new Vulnerability("VULN-004", "src/main/Util.java", 10, "code", Severity.MEDIUM, "XSS", "Reflected XSS", "Java");

        List<Vulnerability> result = triageService.triageFindings(List.of(vuln), "/workspace");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getTriageActionable());
        assertEquals("TRIAGE_FALLBACK", result.get(0).getTriageClassification());
    }

    @Test
    void testTriageDisabled() {
        jevProperties.getTriage().setEnabled(false);

        Vulnerability vuln = new Vulnerability("VULN-005", "src/main/Util.java", 10, "code", Severity.MEDIUM, "XSS", "Reflected XSS", "Java");

        List<Vulnerability> result = triageService.triageFindings(List.of(vuln), "/workspace");

        assertEquals(1, result.size());
        assertNull(result.get(0).getTriageActionable());
    }
}

