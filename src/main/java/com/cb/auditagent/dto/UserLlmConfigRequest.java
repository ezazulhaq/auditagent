package com.cb.auditagent.dto;

import java.io.Serializable;

public record UserLlmConfigRequest(
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        String supervisorModelName,
        Boolean jevEnabled,
        String jevModelName,
        String jevBaseUrl,
        String jevApiKey
) implements Serializable {
    public UserLlmConfigRequest(String provider, String baseUrl, String apiKey, String modelName, String supervisorModelName) {
        this(provider, baseUrl, apiKey, modelName, supervisorModelName, null, null, null, null);
    }
}

