package com.schwab.urlshortener.service;

import com.schwab.urlshortener.config.CacheConfig;
import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.exception.GoneException;
import com.schwab.urlshortener.exception.NotFoundException;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.validation.UrlSafetyValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
public class UrlService {

    private final UrlMappingRepository repository;
    private final UrlSafetyValidator urlSafetyValidator;
    private final CodeGenerator codeGenerator;
    private final AnalyticsService analyticsService;
    private final UrlLookupCache urlLookupCache;

    public UrlService(UrlMappingRepository repository, UrlSafetyValidator urlSafetyValidator,
                       CodeGenerator codeGenerator, AnalyticsService analyticsService,
                       UrlLookupCache urlLookupCache) {
        this.repository = repository;
        this.urlSafetyValidator = urlSafetyValidator;
        this.codeGenerator = codeGenerator;
        this.analyticsService = analyticsService;
        this.urlLookupCache = urlLookupCache;
    }

    @Transactional
    public UrlMapping shorten(String longUrl, Instant expiresAt) {
        urlSafetyValidator.validate(longUrl);

        // id is assigned on insert (IDENTITY), so it's available immediately below.
        UrlMapping mapping = repository.save(new UrlMapping(null, longUrl, expiresAt));
        mapping.setCode(codeGenerator.encode(mapping.getId()));
        mapping = repository.save(mapping);
        log.info("Shortened code={} -> {}", mapping.getCode(), longUrl);
        return mapping;
    }

    @Transactional(readOnly = true)
    public UrlMapping getMetadata(String code) {
        UrlMapping mapping = findOrThrow(code);
        assertRedirectable(mapping, code);
        return mapping;
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        UrlMapping cached = urlLookupCache.findByCode(code);
        if (cached == null) {
            throw new NotFoundException("Unknown short code: " + code);
        }
        assertRedirectable(cached, code);
        log.debug("Resolved code={} -> {}", code, cached.getLongUrl());
        // Fire-and-forget: keeps click accounting off the redirect response path.
        analyticsService.recordClickAsync(code);
        return cached.getLongUrl();
    }

    // No assertRedirectable: stats stay queryable after a link is disabled/expired.
    @Transactional(readOnly = true)
    public UrlMapping getStats(String code) {
        return findOrThrow(code);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.URL_MAPPINGS_CACHE, key = "#code")
    public void disable(String code) {
        UrlMapping mapping = findOrThrow(code);
        mapping.disable();
        repository.save(mapping);
        log.info("Disabled code={}", code);
    }

    private UrlMapping findOrThrow(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Unknown short code: " + code));
    }

    private void assertRedirectable(UrlMapping mapping, String code) {
        if (mapping.isDisabled()) {
            throw new GoneException("Short code has been disabled: " + code);
        }
        if (mapping.isExpired()) {
            throw new GoneException("Short code has expired: " + code);
        }
    }
}
