package com.cb.auditagent.service;

public class ScanTimeoutException extends ScanExecutionException {
    public ScanTimeoutException(String message) {
        super(message);
    }
}
