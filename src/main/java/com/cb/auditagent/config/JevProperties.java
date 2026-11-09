package com.cb.auditagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auditagent.jev")
public class JevProperties {
    private boolean enabled = true;
    private String baseUrl = "https://openrouter.ai/api/v1";
    private String apiKey = "";
    private String modelName = "typesafe/jev-1.14";
    private int timeoutMs = 3000;
    private int maxRetries = 1;
    private TriageProperties triage = new TriageProperties();
    private SupervisorProperties supervisor = new SupervisorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public TriageProperties getTriage() {
        return triage;
    }

    public void setTriage(TriageProperties triage) {
        this.triage = triage;
    }

    public SupervisorProperties getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(SupervisorProperties supervisor) {
        this.supervisor = supervisor;
    }

    public static class TriageProperties {
        private boolean enabled = true;
        private double confidenceThreshold = 0.75;
        private double criticalConfidenceThreshold = 0.95;
        private int maxBatchSize = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public double getCriticalConfidenceThreshold() {
            return criticalConfidenceThreshold;
        }

        public void setCriticalConfidenceThreshold(double criticalConfidenceThreshold) {
            this.criticalConfidenceThreshold = criticalConfidenceThreshold;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }
    }

    public static class SupervisorProperties {
        private boolean enabled = true;
        private boolean fallbackToChatModel = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFallbackToChatModel() {
            return fallbackToChatModel;
        }

        public void setFallbackToChatModel(boolean fallbackToChatModel) {
            this.fallbackToChatModel = fallbackToChatModel;
        }
    }
}

