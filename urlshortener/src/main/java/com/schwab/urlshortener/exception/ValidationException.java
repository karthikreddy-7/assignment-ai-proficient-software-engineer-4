package com.schwab.urlshortener.exception;

/** Thrown for validation failures outside Bean Validation (e.g. UrlSafetyValidator). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
