package com.cb.auditagent.service;

public class ScanExecutionException extends RuntimeException {
    public ScanExecutionException(String message) {
        super(message);
    }

    public ScanExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
