package com.schwab.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(
        @NotBlank
        @Size(max = 2048)
        String longUrl,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {
}
