package com.schwab.urlshortener.service;

import com.schwab.urlshortener.config.AsyncConfig;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Click accounting, off the redirect hot path; delegates to the atomic UPDATE below. */
@Slf4j
@Service
public class AnalyticsService {

    private final UrlMappingRepository repository;

    public AnalyticsService(UrlMappingRepository repository) {
        this.repository = repository;
    }

    @Async(AsyncConfig.ANALYTICS_EXECUTOR)
    @Transactional
    public void recordClickAsync(String code) {
        int updated = repository.incrementClickCount(code, Instant.now());
        if (updated == 0) {
            log.warn("Click for code={} not recorded - code no longer exists", code);
        } else {
            log.debug("Recorded click for code={}", code);
        }
    }
}
