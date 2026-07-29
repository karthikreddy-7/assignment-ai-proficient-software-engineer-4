package com.schwab.urlshortener.service;

import com.schwab.urlshortener.config.CacheConfig;
import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class UrlLookupCache {

    private final UrlMappingRepository repository;

    public UrlLookupCache(UrlMappingRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = CacheConfig.URL_MAPPINGS_CACHE, key = "#code", unless = "#result == null")
    public UrlMapping findByCode(String code) {
        return repository.findByCode(code).orElse(null);
    }
}
