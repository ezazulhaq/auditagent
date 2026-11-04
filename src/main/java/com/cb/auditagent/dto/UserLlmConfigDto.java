package com.cb.auditagent.dto;

import java.io.Serializable;

public record UserLlmConfigDto(
        boolean configured,
        boolean userKeysRequired,
        String provider,
        String baseUrl,
        String modelName,
        String supervisorModelName,
        String maskedApiKey,
        boolean jevEnabled,
        String jevModelName,
        String jevBaseUrl,
        String maskedJevApiKey,
        boolean systemJevEnabled,
        String defaultJevModelName
) implements Serializable {
    public UserLlmConfigDto(
            boolean configured,
            boolean userKeysRequired,
            String provider,
            String baseUrl,
            String modelName,
            String supervisorModelName,
            String maskedApiKey
    ) {
        this(configured, userKeysRequired, provider, baseUrl, modelName, supervisorModelName, maskedApiKey,
                true, null, null, null, true, "typesafe/jev-1.14");
    }
}

