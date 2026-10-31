package com.cb.auditagent.service;

public class UserLlmConfigurationException extends RuntimeException {
    private final String errorCode;

    public UserLlmConfigurationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public UserLlmConfigurationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

