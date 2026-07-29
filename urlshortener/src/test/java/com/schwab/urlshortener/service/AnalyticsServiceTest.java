package com.schwab.urlshortener.service;

import com.schwab.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UrlMappingRepository repository;

    private AnalyticsService analyticsService;

    @Test
    void delegatesToTheAtomicUpdateRatherThanReadThenWrite() {
        analyticsService = new AnalyticsService(repository);
        when(repository.incrementClickCount(eq("abc123"), any())).thenReturn(1);

        analyticsService.recordClickAsync("abc123");

        verify(repository).incrementClickCount(eq("abc123"), any());
    }

    @Test
    void doesNotThrowIfCodeNoLongerExists() {
        analyticsService = new AnalyticsService(repository);
        when(repository.incrementClickCount(eq("gone"), any())).thenReturn(0);

        analyticsService.recordClickAsync("gone");

        verify(repository).incrementClickCount(eq("gone"), any());
    }
}
