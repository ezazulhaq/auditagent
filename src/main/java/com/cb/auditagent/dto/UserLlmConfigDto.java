package com.cb.auditagent.dto;

import java.io.Serializable;

public record UserLlmConfigDto(
        boolean configured,
        boolean userKeysRequired,
        String provider,
        String baseUrl,
        String modelName,
        String supervisorModelName,
        String maskedApiKey
) implements Serializable {
}

