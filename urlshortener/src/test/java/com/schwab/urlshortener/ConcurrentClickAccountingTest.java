package com.schwab.urlshortener;

import com.schwab.urlshortener.config.AsyncConfig;
import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.service.AnalyticsService;
import com.schwab.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Proves concurrent clicks on the same code are all counted (no lost updates). */
@SpringBootTest
class ConcurrentClickAccountingTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    @Qualifier(AsyncConfig.ANALYTICS_EXECUTOR)
    private ThreadPoolTaskExecutor analyticsExecutor;

    @Test
    void everyConcurrentClickOnTheSameCodeIsCounted() {
        UrlMapping mapping = urlService.shorten("https://example.com/concurrent-clicks", null);
        String code = mapping.getCode();

        int clicks = 50;
        for (int i = 0; i < clicks; i++) {
            analyticsService.recordClickAsync(code);
        }

        // Wait for the pool to drain instead of a fixed sleep.
        await().atMost(Duration.ofSeconds(10)).until(() ->
                analyticsExecutor.getThreadPoolExecutor().getActiveCount() == 0
                        && analyticsExecutor.getThreadPoolExecutor().getQueue().isEmpty());

        long actualCount = repository.findByCode(code).orElseThrow().getClickCount();

        assertThat(actualCount)
                .as("expected all %d concurrent clicks on the same code to be counted, "
                        + "but got %d - a lower count means increments were lost to a "
                        + "read-then-write race", clicks, actualCount)
                .isEqualTo(clicks);
    }
}
