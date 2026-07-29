package com.schwab.urlshortener.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** Bounded thread pool for click accounting, off the redirect hot path. */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ANALYTICS_EXECUTOR = "analyticsExecutor";

    @Bean(name = ANALYTICS_EXECUTOR)
    public ThreadPoolTaskExecutor analyticsExecutor(
            @Value("${app.async.core-pool-size}") int corePoolSize,
            @Value("${app.async.max-pool-size}") int maxPoolSize,
            @Value("${app.async.queue-capacity}") int queueCapacity,
            MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        // Exposes queue depth so backpressure is visible in metrics.
        Gauge.builder("urlshortener.analytics.queue.size", executor,
                        e -> e.getThreadPoolExecutor().getQueue().size())
                .description("Pending analytics tasks waiting for a worker thread")
                .register(meterRegistry);

        return executor;
    }
}
