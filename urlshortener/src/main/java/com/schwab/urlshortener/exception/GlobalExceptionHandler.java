package com.schwab.urlshortener.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/** Translates every thrown exception into the one consistent {@link ErrorResponse} shape. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ErrorResponse> handleGone(GoneException ex, HttpServletRequest req) {
        return build(HttpStatus.GONE, "GONE", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, req, ex);
    }

    // Generic safety net for any unique-constraint violation.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists", req, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", req, ex);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message,
                                                 HttpServletRequest req, Exception ex) {
        String traceId = traceId();
        if (status.is5xxServerError()) {
            log.error("[{}] {} {} -> {}", traceId, req.getMethod(), req.getRequestURI(), message, ex);
        } else {
            log.warn("[{}] {} {} -> {} ({})", traceId, req.getMethod(), req.getRequestURI(), error, message);
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(error, message, req.getRequestURI(), traceId));
    }

    // Falls back to a fresh UUID if CorrelationIdFilter hasn't populated MDC yet.
    private String traceId() {
        String mdcTraceId = MDC.get("traceId");
        return mdcTraceId != null ? mdcTraceId : UUID.randomUUID().toString().substring(0, 8);
    }
}
