package com.cb.auditagent.service;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class LlmErrorClassifierTest {

    @Test
    void testModelNotFoundExceptionOpenRouterJson() {
        String rawJson = "{\"error\":{\"message\":\"anthropic/claude-3.5-sonnet is no longer available on OpenRouter: no provider serves this model anymore. See available models at https://openrouter.ai/models\",\"code\":404},\"user_id\":\"org_31FsC5pCnLJfY8FqEMSUuIxNDSF\"}";
        Exception inner = new RuntimeException("dev.langchain4j.exception.ModelNotFoundException: " + rawJson);
        Exception middle = new ExecutionException(inner);
        Exception outer = new RuntimeException("java.util.concurrent.ExecutionException: " + middle.getMessage(), middle);

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(outer);

        assertEquals("MODEL_NOT_FOUND", classified.errorCode());
        assertTrue(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("404 Not Found"));
        assertTrue(classified.userMessage().contains("anthropic/claude-3.5-sonnet is no longer available on OpenRouter"));
        assertTrue(classified.userMessage().contains("Profile Settings"));
        assertFalse(classified.userMessage().contains("ExecutionException"));
        assertFalse(classified.userMessage().contains("RuntimeException"));
        assertFalse(classified.userMessage().contains("org_31FsC5pCnLJfY8FqEMSUuIxNDSF"));
    }

    @Test
    void testUnauthorizedException() {
        String rawJson = "{\"error\":{\"message\":\"Invalid API key provided. Check your key at https://openrouter.ai/settings/keys\",\"code\":401}}";
        Exception ex = new ExecutionException(new RuntimeException("401 Unauthorized: " + rawJson));

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("CREDENTIALS_INVALID", classified.errorCode());
        assertTrue(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("401 Unauthorized"));
        assertTrue(classified.userMessage().contains("Invalid API key provided"));
        assertTrue(classified.userMessage().contains("Profile Settings"));
        assertFalse(classified.userMessage().contains("ExecutionException"));
    }

    @Test
    void testQuotaExceeded() {
        Exception ex = new RuntimeException("402 Payment Required: You have exceeded your current quota or run out of credits.");

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("QUOTA_EXCEEDED", classified.errorCode());
        assertTrue(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("402 Payment Required"));
        assertTrue(classified.userMessage().contains("Profile Settings"));
    }

    @Test
    void testRateLimit() {
        Exception ex = new RuntimeException("HTTP 429 Too Many Requests: Rate limit exceeded.");

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("RATE_LIMITED", classified.errorCode());
        assertFalse(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("429 Too Many Requests"));
    }

    @Test
    void testContextLengthExceeded() {
        Exception ex = new RuntimeException("Error: maximum context length exceeded for this model.");

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("CONTEXT_LENGTH_EXCEEDED", classified.errorCode());
        assertFalse(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("Context length exceeded"));
    }

    @Test
    void testConnectionFailed() {
        Exception ex = new ExecutionException(new ConnectException("Connection refused to https://api.custom-llm.com/v1"));

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("CONNECTION_FAILED", classified.errorCode());
        assertTrue(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("Connection to LLM provider failed"));
    }

    @Test
    void testStaleBase() {
        Exception ex = new IllegalStateException("STALE_BASE: branch has advanced");

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("STALE_BASE", classified.errorCode());
        assertFalse(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("The repository branch has advanced"));
    }

    @Test
    void testUserLlmConfigurationException() {
        Exception ex = new UserLlmConfigurationException("User LLM API key and model must be configured in user profile", "CREDENTIALS_MISSING");

        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(ex);

        assertEquals("CREDENTIALS_MISSING", classified.errorCode());
        assertTrue(classified.requiresLlmConfig());
        assertTrue(classified.userMessage().contains("User LLM API key and model must be configured"));
        assertTrue(classified.userMessage().contains("Profile"));
    }

    @Test
    void testStripExceptionPrefixes() {
        String dirty = "java.util.concurrent.ExecutionException: java.lang.RuntimeException: dev.langchain4j.exception.ModelNotFoundException: Clean message here";
        String clean = LlmErrorClassifier.stripExceptionPrefixes(dirty);
        assertEquals("Clean message here", clean);
    }
}
