package com.cb.auditagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auditagent.llm")
public class LlmProperties {

    private boolean userKeysRequired = true;
    private String mode = "user";
    private String provider = "openrouter";
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
        private String supervisorModelName = "typesafe/jev-1.13";

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

