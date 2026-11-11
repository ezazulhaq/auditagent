package com.cb.auditagent.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encapsulates token usage tracking and metric recording.
 * Follows Single Responsibility Principle (SRP).
 */
@Component
public class LlmTokenTracker {

    private final MeterRegistry meterRegistry;
    private final DatabaseService databaseService;

    @Autowired
    public LlmTokenTracker(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) DatabaseService databaseService) {
        this.meterRegistry = meterRegistry;
        this.databaseService = databaseService;
    }

    public void recordTokens(String modelName, Integer tokens, String threadId, String runId, String vulnId) {
        if (meterRegistry != null && tokens != null && tokens > 0 && modelName != null) {
            meterRegistry.counter("ai.tokens.consumed", "model", modelName).increment(tokens);
        }
        if (databaseService != null && tokens != null && tokens > 0 && threadId != null) {
            databaseService.saveTokenUsage(threadId, runId, vulnId, modelName, tokens);
        }
    }
}
