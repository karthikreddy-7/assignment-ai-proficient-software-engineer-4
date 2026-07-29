package com.schwab.urlshortener.service;

import com.schwab.urlshortener.config.CacheConfig;
import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UrlLookupCache {

    private final UrlMappingRepository repository;
    private final CacheManager cacheManager;

    public UrlLookupCache(UrlMappingRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    public UrlMapping findByCode(String code) {
        Cache cache = cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
        Cache.ValueWrapper cached = cache.get(code);
        if (cached != null) {
            log.info("code={} cache=HIT", code);
            return (UrlMapping) cached.get();
        }

        log.info("code={} cache=MISS, querying repository", code);
        UrlMapping mapping = repository.findByCode(code).orElse(null);
        log.info("code={} repository response={}", code, mapping != null ? "found" : "not_found");

        if (mapping != null) {
            cache.put(code, mapping);
        }
        return mapping;
    }
}
