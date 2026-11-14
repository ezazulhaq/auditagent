package com.cb.auditagent.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LlmTokenAuditServiceTest {

    private MeterRegistry mockMeterRegistry;
    private DatabaseService mockDatabaseService;
    private Counter mockCounter;
    private LlmTokenAuditService auditService;

    @BeforeEach
    void setUp() {
        mockMeterRegistry = mock(MeterRegistry.class);
        mockDatabaseService = mock(DatabaseService.class);
        mockCounter = mock(Counter.class);

        when(mockMeterRegistry.counter("ai.tokens.consumed", "model", "test-model")).thenReturn(mockCounter);

        auditService = new LlmTokenAuditService(mockMeterRegistry, mockDatabaseService);
    }

    @Test
    @DisplayName("recordTokens records metric and persists to database when tokens > 0")
    void testRecordTokensPositive() {
        auditService.recordTokens("test-model", 150, "thread-1", "run-1", "VULN-001");

        verify(mockCounter).increment(150.0);
        verify(mockDatabaseService).saveTokenUsage("thread-1", "run-1", "VULN-001", "test-model", 150);
    }

    @Test
    @DisplayName("recordTokens ignores null or zero tokens")
    void testRecordTokensZeroOrNull() {
        auditService.recordTokens("test-model", null, "thread-1", "run-1", "VULN-001");
        auditService.recordTokens("test-model", 0, "thread-1", "run-1", "VULN-001");
        auditService.recordTokens("test-model", -10, "thread-1", "run-1", "VULN-001");

        verifyNoInteractions(mockMeterRegistry);
        verifyNoInteractions(mockDatabaseService);
    }
}
