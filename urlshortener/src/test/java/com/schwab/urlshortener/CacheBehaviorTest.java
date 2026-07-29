package com.schwab.urlshortener;

import com.schwab.urlshortener.config.CacheConfig;
import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheBehaviorTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void resolvePopulatesTheCacheOnFirstLookup() {
        UrlMapping mapping = urlService.shorten("https://example.com/cache-behavior", Instant.now().plusSeconds(3600));

        urlService.resolve(mapping.getCode());

        Cache.ValueWrapper cached = urlMappingsCache().get(mapping.getCode());
        assertThat(cached).isNotNull();
        assertThat(((UrlMapping) cached.get()).getLongUrl()).isEqualTo("https://example.com/cache-behavior");
    }

    @Test
    void disableEvictsTheCachedEntry() {
        UrlMapping mapping = urlService.shorten("https://example.com/cache-evict", Instant.now().plusSeconds(3600));
        urlService.resolve(mapping.getCode());
        assertThat(urlMappingsCache().get(mapping.getCode())).isNotNull();

        urlService.disable(mapping.getCode());

        assertThat(urlMappingsCache().get(mapping.getCode())).isNull();
    }

    private Cache urlMappingsCache() {
        return cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
    }
}
