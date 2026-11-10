package com.mikhail.marketdata.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.mikhail.marketdata.resilience.ExternalSourceUnavailableException;

import java.time.Instant;
import java.util.Map;

/** Ошибки REST API в едином формате {@code {message, timestamp}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TickerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(TickerNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ExternalSourceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> sourceUnavailable(ExternalSourceUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "Внешний источник данных недоступен: " + exception.getSourceName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "message", message,
                "timestamp", Instant.now().toString()));
    }
}
