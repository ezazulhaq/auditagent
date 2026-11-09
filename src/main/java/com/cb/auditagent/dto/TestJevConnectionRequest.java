package com.cb.auditagent.dto;

import java.io.Serializable;

public record TestJevConnectionRequest(
        String baseUrl,
        String apiKey,
        String modelName
) implements Serializable {
}

