package com.schwab.urlshortener.dto;

import java.time.Instant;

public record StatsResponse(
        String code,
        long totalClicks,
        Instant lastClickedAt) {
}
