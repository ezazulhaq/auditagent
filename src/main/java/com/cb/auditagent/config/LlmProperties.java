package com.cb.auditagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AuditAgent LLM integrations.
 * Consolidated in Phase 3 under auditagent.llm.*.
 */
@Component
@ConfigurationProperties(prefix = "auditagent.llm")
public class LlmProperties {

    private boolean userKeysRequired = true;
    private String mode = "user";
    private String provider = "openrouter";
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private String supervisorModelName;
    private OpenAiProperties openai = new OpenAiProperties();

    public boolean isUserKeysRequired() {
        return userKeysRequired;
    }

    public void setUserKeysRequired(boolean userKeysRequired) {
        this.userKeysRequired = userKeysRequired;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl != null ? baseUrl : openai.getBaseUrl();
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        if (this.openai != null) {
            this.openai.setBaseUrl(baseUrl);
        }
    }

    public String getApiKey() {
        return apiKey != null ? apiKey : openai.getApiKey();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        if (this.openai != null) {
            this.openai.setApiKey(apiKey);
        }
    }

    public String getModelName() {
        return modelName != null ? modelName : openai.getModelName();
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
        if (this.openai != null) {
            this.openai.setModelName(modelName);
        }
    }

    public String getSupervisorModelName() {
        return supervisorModelName != null ? supervisorModelName : openai.getSupervisorModelName();
    }

    public void setSupervisorModelName(String supervisorModelName) {
        this.supervisorModelName = supervisorModelName;
        if (this.openai != null) {
            this.openai.setSupervisorModelName(supervisorModelName);
        }
    }

    public OpenAiProperties getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiProperties openai) {
        this.openai = openai;
    }

    public static class OpenAiProperties {
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String apiKey = "";
        private String modelName = "z-ai/glm-5.3-flash";
        private String supervisorModelName = "typesafe/jev-1.14";

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

        public String getSupervisorModelName() {
            return supervisorModelName;
        }

        public void setSupervisorModelName(String supervisorModelName) {
            this.supervisorModelName = supervisorModelName;
        }
    }
}
