package com.cb.auditagent.dto;

import java.io.Serializable;

public record TestConnectionResponse(
        boolean success,
        String message,
        String errorCode
) implements Serializable {
    public static TestConnectionResponse ok(String message) {
        return new TestConnectionResponse(true, message, null);
    }

    public static TestConnectionResponse fail(String message, String errorCode) {
        return new TestConnectionResponse(false, message, errorCode);
    }
}

