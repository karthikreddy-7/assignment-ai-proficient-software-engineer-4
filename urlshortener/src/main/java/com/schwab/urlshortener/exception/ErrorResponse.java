package com.schwab.urlshortener.exception;

import java.time.Instant;

/** Single error envelope shape for every 4xx/5xx response. */
public record ErrorResponse(
        String error,
        String message,
        String path,
        Instant timestamp,
        String traceId) {

    public static ErrorResponse of(String error, String message, String path, String traceId) {
        return new ErrorResponse(error, message, path, Instant.now(), traceId);
    }
}
