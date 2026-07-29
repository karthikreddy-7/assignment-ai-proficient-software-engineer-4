package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.domain.UrlMapping;
import com.schwab.urlshortener.dto.CreateUrlRequest;
import com.schwab.urlshortener.dto.StatsResponse;
import com.schwab.urlshortener.dto.UrlResponse;
import com.schwab.urlshortener.service.UrlService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;
    private final MeterRegistry meterRegistry;

    public UrlController(UrlService urlService, MeterRegistry meterRegistry) {
        this.urlService = urlService;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<UrlResponse> shorten(@Valid @RequestBody CreateUrlRequest request,
                                                HttpServletRequest servletRequest) {
        log.info("Request received: POST /api/v1/urls");
        UrlMapping mapping = urlService.shorten(request.longUrl(), request.expiresAt());
        meterRegistry.counter("urlshortener.shorten.count").increment();
        UrlResponse body = toResponse(mapping, servletRequest);
        log.info("Request completed: POST /api/v1/urls -> 201 code={}", mapping.getCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create(body.shortUrl()))
                .body(body);
    }

    @GetMapping("/{code}")
    public UrlResponse getMetadata(@PathVariable String code, HttpServletRequest servletRequest) {
        log.info("Request received: GET /api/v1/urls/{}", code);
        UrlMapping mapping = urlService.getMetadata(code);
        return toResponse(mapping, servletRequest);
    }

    @GetMapping("/{code}/stats")
    public StatsResponse getStats(@PathVariable String code) {
        log.info("Request received: GET /api/v1/urls/{}/stats", code);
        UrlMapping mapping = urlService.getStats(code);
        return new StatsResponse(mapping.getCode(), mapping.getClickCount(), mapping.getLastClickedAt());
    }

    @DeleteMapping("/{code}")
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        log.info("Request received: DELETE /api/v1/urls/{}", code);
        urlService.disable(code);
        return ResponseEntity.noContent().build();
    }

    private UrlResponse toResponse(UrlMapping mapping, HttpServletRequest servletRequest) {
        String shortUrl = ServletUriComponentsBuilder.fromContextPath(servletRequest)
                .path("/" + mapping.getCode())
                .toUriString();
        return new UrlResponse(mapping.getCode(), shortUrl, mapping.getLongUrl(),
                mapping.getCreatedAt(), mapping.getExpiresAt());
    }
}
