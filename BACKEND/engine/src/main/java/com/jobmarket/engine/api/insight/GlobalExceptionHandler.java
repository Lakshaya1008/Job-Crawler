package com.jobmarket.engine.api.insight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Catches exceptions thrown from any controller and returns
 * clean, consistent JSON error responses instead of stack traces.
 *
 * WHY @RestControllerAdvice?
 * Without this, if InsightService throws an exception,
 * Spring returns a 500 with an HTML error page or raw stack trace.
 * That exposes internal details and is ugly for API callers.
 *
 * With this, every exception gets a clean JSON response:
 * {
 *   "error": "Something went wrong",
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 500
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", ex.getMessage(),
                "status", 400,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Log the full stack trace internally
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        // Return minimal info to caller — never expose stack traces externally
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error — check logs",
                "status", 500,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
