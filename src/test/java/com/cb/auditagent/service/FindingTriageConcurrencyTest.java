package com.cb.auditagent.service;

import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevQuestion;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FindingTriageConcurrencyTest {

    private JevClient jevClient;
    private JevProperties jevProperties;
    private FindingTriageService service;

    @BeforeEach
    void setUp() {
        jevClient = mock(JevClient.class);
        jevProperties = new JevProperties();
        jevProperties.setEnabled(true);
        jevProperties.setApiKey("test-key");
        jevProperties.getTriage().setEnabled(true);
        jevProperties.getTriage().setConcurrency(3);
        jevProperties.getTriage().setMaxRetries(2);
        jevProperties.getTriage().setBackoffInitialMs(10); // fast backoff for unit tests

        when(jevClient.isEnabled()).thenReturn(true);
        service = new FindingTriageService(jevClient, jevProperties, null, null);
    }

    @Test
    void semaphoreBoundsConcurrentHttpCallsUnderLoad() throws Exception {
        int totalFindings = 12;
        List<Vulnerability> findings = new ArrayList<>();
        for (int i = 0; i < totalFindings; i++) {
            Vulnerability v = new Vulnerability();
            v.setId("VULN-" + i);
            v.setRuleId("rule-" + i);
            v.setFilePath("File" + i + ".java");
            v.setSeverity(Severity.MEDIUM);
            findings.add(v);
        }

        AtomicInteger currentConcurrentCalls = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrentCalls = new AtomicInteger(0);

        com.cb.auditagent.client.jev.JevResponse response = new com.cb.auditagent.client.jev.JevResponse();
        response.setAnswers(java.util.Map.of("actionability", new com.cb.auditagent.client.jev.JevAnswer("ACTIONABLE_VULNERABILITY", 0.9)));

        when(jevClient.execute(any(), eq("finding_triage"))).thenAnswer(inv -> {
            int active = currentConcurrentCalls.incrementAndGet();
            maxObservedConcurrentCalls.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(30);
            } finally {
                currentConcurrentCalls.decrementAndGet();
            }
            return response;
        });

        List<Vulnerability> result = service.triageFindings(findings, "/tmp/ws");

        assertEquals(totalFindings, result.size());
        assertTrue(maxObservedConcurrentCalls.get() <= 3,
                "Observed concurrency (" + maxObservedConcurrentCalls.get() + ") must not exceed configured limit of 3");
    }

    @Test
    void recoversOnTransient429RateLimitError() throws Exception {
        Vulnerability v = new Vulnerability();
        v.setId("VULN-RETRY");
        v.setSeverity(Severity.HIGH);

        com.cb.auditagent.client.jev.JevResponse successResponse = new com.cb.auditagent.client.jev.JevResponse();
        successResponse.setAnswers(java.util.Map.of("actionability", new com.cb.auditagent.client.jev.JevAnswer("ACTIONABLE_VULNERABILITY", 0.98)));

        // Fail with 429 on first call, succeed on retry
        when(jevClient.execute(any(), eq("finding_triage")))
                .thenThrow(new IOException("Jev request failed with HTTP 429: Too Many Requests"))
                .thenReturn(successResponse);

        service.triageSingleFinding(v);

        verify(jevClient, times(2)).execute(any(), eq("finding_triage"));
        assertTrue(v.getTriageActionable());
        assertEquals("ACTIONABLE_VULNERABILITY", v.getTriageClassification());
        assertEquals(0.98, v.getTriageConfidence());
    }

    @Test
    void marksFindingUnverifiedWhen429RetriesExhaustedWithoutFailing() throws Exception {
        Vulnerability v = new Vulnerability();
        v.setId("VULN-EXHAUSTED");
        v.setSeverity(Severity.HIGH);

        // Continually fail with 429
        when(jevClient.execute(any(), eq("finding_triage")))
                .thenThrow(new IOException("Jev request failed with HTTP 429: Rate limit exceeded"));

        assertDoesNotThrow(() -> service.triageSingleFinding(v));

        // Initial attempt + 2 retries = 3 calls
        verify(jevClient, times(3)).execute(any(), eq("finding_triage"));
        assertTrue(v.getTriageActionable());
        assertEquals("UNVERIFIED", v.getTriageClassification());
        assertEquals(0.0, v.getTriageConfidence());
    }
}
