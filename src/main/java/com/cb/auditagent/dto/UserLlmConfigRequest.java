package com.cb.auditagent.dto;

import java.io.Serializable;

public record UserLlmConfigRequest(
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        String supervisorModelName
) implements Serializable {
}

