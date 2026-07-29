package com.schwab.urlshortener.exception;

/** Thrown for a short code that exists but is expired or disabled. Maps to 410 Gone. */
public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
