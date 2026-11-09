package com.cb.auditagent.client.jev;

import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class JevClient {
    private static final Logger logger = LoggerFactory.getLogger(JevClient.class);

    private final JevProperties properties;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HttpClient httpClient;

    @Autowired
    public JevClient(
            JevProperties properties,
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this(properties, llmProperties, objectMapper, meterRegistry, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public JevClient(
            JevProperties properties,
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            HttpClient httpClient) {
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && (!resolveApiKey(null).isBlank());
    }

    public JevResponse execute(JevRequest request, String operation) throws Exception {
        return execute(request, operation, null, null);
    }

    public JevResponse execute(JevRequest request, String operation, String explicitApiKey, String explicitBaseUrl) throws Exception {
        String apiKey = resolveApiKey(explicitApiKey);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Cannot execute Jev request: No API key configured.");
        }

        String endpointUrl = resolveEndpointUrl(explicitBaseUrl);
        String requestJson = objectMapper.writeValueAsString(request);
        int maxRetries = Math.max(0, properties.getMaxRetries());
        int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 3000;

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long startNanos = System.nanoTime();
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("HTTP-Referer", "https://auditagent.local")
                        .header("X-Title", "AuditAgent")
                        .timeout(Duration.ofMillis(timeoutMs))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long durationNanos = System.nanoTime() - startNanos;

                if (meterRegistry != null) {
                    meterRegistry.timer("jev.request.latency", "operation", operation).record(durationNanos, TimeUnit.NANOSECONDS);
                }

                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    JevResponse jevResponse = objectMapper.readValue(response.body(), JevResponse.class);
                    if (meterRegistry != null) {
                        meterRegistry.counter("jev.requests.total", "operation", operation, "status", "success").increment();
                        if (jevResponse.getUsage() != null && jevResponse.getUsage().getTotalTokens() != null) {
                            meterRegistry.counter("ai.tokens.consumed", "model", properties.getModelName())
                                    .increment(jevResponse.getUsage().getTotalTokens());
                        }
                    }
                    return jevResponse;
                } else if (statusCode >= 500 && attempt < maxRetries) {
                    logger.warn("Jev server error {} on attempt {}, retrying...", statusCode, attempt + 1);
                } else {
                    if (meterRegistry != null) {
                        meterRegistry.counter("jev.requests.total", "operation", operation, "status", "http_error").increment();
                    }
                    throw new IOException("Jev request failed with HTTP " + statusCode + ": " + response.body());
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries && isRetryable(e)) {
                    logger.warn("Jev request failed with {} on attempt {}, retrying: {}", e.getClass().getSimpleName(), attempt + 1, e.getMessage());
                    continue;
                }
                break;
            }
        }

        if (meterRegistry != null) {
            meterRegistry.counter("jev.requests.total", "operation", operation, "status", "failure").increment();
        }
        throw (lastException != null ? lastException : new IOException("Jev request failed after " + (maxRetries + 1) + " attempts"));
    }

    private boolean isRetryable(Exception e) {
        return e instanceof java.io.IOException || e instanceof java.net.http.HttpConnectTimeoutException;
    }

    public String resolveApiKey(String explicitApiKey) {
        if (explicitApiKey != null && !explicitApiKey.isBlank()) {
            return explicitApiKey.trim();
        }
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            return properties.getApiKey().trim();
        }
        if (llmProperties != null && llmProperties.getOpenai() != null && llmProperties.getOpenai().getApiKey() != null) {
            return llmProperties.getOpenai().getApiKey().trim();
        }
        return "";
    }

    public String resolveEndpointUrl(String explicitBaseUrl) {
        String base = (explicitBaseUrl != null && !explicitBaseUrl.isBlank()) ? explicitBaseUrl.trim() : properties.getBaseUrl().trim();
        if (base.endsWith("/systemone")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "systemone";
        }
        return base + "/systemone";
    }

    public JevProperties getProperties() {
        return properties;
    }
}

