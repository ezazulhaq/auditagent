package com.cb.auditagent.dto;

import java.io.Serializable;

public record TestConnectionRequest(
        String provider,
        String baseUrl,
        String apiKey,
        String modelName
) implements Serializable {
}

