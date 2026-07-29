package com.schwab.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Bounded, TTL'd cache-aside for the redirect hot path (code -> UrlMapping). */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String URL_MAPPINGS_CACHE = "urlMappings";

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.max-size}") long maxSize,
            @Value("${app.cache.ttl-minutes}") long ttlMinutes,
            MeterRegistry meterRegistry) {
        CaffeineCacheManager manager = new CaffeineCacheManager(URL_MAPPINGS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats());

        // Forces the cache into existence now so its stats can be bound to Micrometer.
        CaffeineCache springCache = (CaffeineCache) manager.getCache(URL_MAPPINGS_CACHE);
        CaffeineCacheMetrics.monitor(meterRegistry, springCache.getNativeCache(), URL_MAPPINGS_CACHE);

        return manager;
    }
}
