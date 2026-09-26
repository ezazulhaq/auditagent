package com.cb.auditagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cb.auditagent.service.MemoryRedactor;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final MemoryRedactor redactor;

    public ApiExceptionHandler(MemoryRedactor redactor) {
        this.redactor = redactor;
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<?> forbidden(SecurityException exception) {
        log.warn("Security exception: {}", safe(exception));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Access denied. You do not have permission for this action.",
                "error", safe(exception)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(IllegalArgumentException exception) {
        log.warn("Bad request: {}", safe(exception));
        return ResponseEntity.badRequest().body(Map.of(
                "message", "The request contains invalid information. Please check your input.",
                "error", safe(exception)));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException exception) {
        log.warn("State conflict: {}", safe(exception));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "The action cannot be completed due to a conflict with the current state.",
                "error", safe(exception)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> genericError(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "An unexpected system error occurred. Please try again later.",
                "error", safe(exception)));
    }

    private String safe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank())
            return exception.getClass().getSimpleName();
        return redactor.redactAndCap(message, 500);
    }
}
