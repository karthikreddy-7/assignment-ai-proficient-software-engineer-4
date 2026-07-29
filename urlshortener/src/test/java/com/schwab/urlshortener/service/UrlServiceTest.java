package com.schwab.urlshortener.service;

import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.exception.GoneException;
import com.schwab.urlshortener.exception.NotFoundException;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.validation.UrlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private AnalyticsService analyticsService;

    private final UrlSafetyValidator urlSafetyValidator = new UrlSafetyValidator();
    private final CodeGenerator codeGenerator = new CodeGenerator();

    private UrlService urlService;

    @BeforeEach
    void wireRealCollaborators() {
        UrlLookupCache urlLookupCache = new UrlLookupCache(repository, new ConcurrentMapCacheManager());
        urlService = new UrlService(repository, urlSafetyValidator, codeGenerator, analyticsService, urlLookupCache);
    }

    @Test
    void shortenGeneratesCodeFromAssignedIdentity() {
        UrlMapping placeholder = mappingWithId(null, "https://example.com", null);
        UrlMapping withId = mappingWithId(42L, "https://example.com", null);
        when(repository.save(any(UrlMapping.class))).thenReturn(withId, withId);

        UrlMapping result = urlService.shorten("https://example.com", null);

        assertThat(result.getCode()).isEqualTo(codeGenerator.encode(42L));
    }

    @Test
    void shortenRejectsUnsafeUrlBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> urlService.shorten("javascript:alert(1)", null))
                .isInstanceOf(com.schwab.urlshortener.exception.ValidationException.class);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void getMetadataThrowsNotFoundForUnknownCode() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.getMetadata("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMetadataThrowsGoneForDisabledLink() {
        UrlMapping mapping = mappingWithId(1L, "https://example.com", null);
        mapping.disable();
        when(repository.findByCode("abc")).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> urlService.getMetadata("abc"))
                .isInstanceOf(GoneException.class);
    }

    @Test
    void getMetadataThrowsGoneForExpiredLink() {
        UrlMapping mapping = mappingWithId(1L, "https://example.com", Instant.now().minus(1, ChronoUnit.DAYS));
        when(repository.findByCode("abc")).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> urlService.getMetadata("abc"))
                .isInstanceOf(GoneException.class);
    }

    @Test
    void getStatsStaysAccessibleForADisabledLink() {
        UrlMapping mapping = mappingWithId(1L, "https://example.com", null);
        setCode(mapping, "abc123");
        mapping.setClickCount(5);
        mapping.disable();
        when(repository.findByCode("abc123")).thenReturn(Optional.of(mapping));

        UrlMapping result = urlService.getStats("abc123");

        assertThat(result.getClickCount()).isEqualTo(5);
    }

    @Test
    void resolveDelegatesClickAccountingToAnalyticsServiceAsynchronously() {
        UrlMapping mapping = mappingWithId(1L, "https://example.com", null);
        setCode(mapping, "abc123");
        when(repository.findByCode("abc123")).thenReturn(Optional.of(mapping));

        String longUrl = urlService.resolve("abc123");

        assertThat(longUrl).isEqualTo("https://example.com");
        // resolve() must delegate, not write directly.
        verify(analyticsService).recordClickAsync("abc123");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void resolveThrowsGoneForDisabledLinkWithoutRecordingAClick() {
        UrlMapping mapping = mappingWithId(1L, "https://example.com", null);
        setCode(mapping, "abc123");
        mapping.disable();
        when(repository.findByCode("abc123")).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> urlService.resolve("abc123")).isInstanceOf(GoneException.class);
        org.mockito.Mockito.verifyNoInteractions(analyticsService);
    }

    private UrlMapping mappingWithId(Long id, String longUrl, Instant expiresAt) {
        UrlMapping mapping = new UrlMapping(null, longUrl, expiresAt);
        if (id != null) {
            setField(mapping, "id", id);
        }
        return mapping;
    }

    private void setCode(UrlMapping mapping, String code) {
        mapping.setCode(code);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
