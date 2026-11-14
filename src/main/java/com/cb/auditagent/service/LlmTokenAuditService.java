package com.cb.auditagent.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for auditing LLM token usage, updating Micrometer metrics,
 * and persisting consumption records for billing and quota tracking.
 * Extracted from LlmService during Phase 3 decomposition.
 */
@Service
public class LlmTokenAuditService {

    private static final Logger logger = LoggerFactory.getLogger(LlmTokenAuditService.class);

    private final MeterRegistry meterRegistry;
    private final DatabaseService databaseService;

    public LlmTokenAuditService(
            MeterRegistry meterRegistry,
            DatabaseService databaseService) {
        this.meterRegistry = meterRegistry;
        this.databaseService = databaseService;
    }

    public void recordTokens(String modelName, Integer tokens, String threadId, String runId, String vulnId) {
        if (tokens == null || tokens <= 0) {
            return;
        }

        String safeModelName = (modelName != null && !modelName.isBlank()) ? modelName : "unknown";

        if (meterRegistry != null) {
            try {
                meterRegistry.counter("ai.tokens.consumed", "model", safeModelName).increment(tokens);
            } catch (Exception e) {
                logger.warn("Failed to increment token metric for model {}: {}", safeModelName, e.getMessage());
            }
        }

        if (databaseService != null && threadId != null && !threadId.isBlank()) {
            try {
                databaseService.saveTokenUsage(threadId, runId, vulnId, safeModelName, tokens);
            } catch (Exception e) {
                logger.warn("Failed to persist token usage for thread {} and model {}: {}", threadId, safeModelName, e.getMessage());
            }
        }
    }
}
