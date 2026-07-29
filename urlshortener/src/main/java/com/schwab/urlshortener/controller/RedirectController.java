package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.exception.GoneException;
import com.schwab.urlshortener.exception.NotFoundException;
import com.schwab.urlshortener.service.UrlService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Root-path redirect (/{code}), not under /api/v1 - a short link has to be short. */
@Slf4j
@RestController
public class RedirectController {

    private final UrlService urlService;
    private final MeterRegistry meterRegistry;

    public RedirectController(UrlService urlService, MeterRegistry meterRegistry) {
        this.urlService = urlService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        log.info("Request received: GET /{}", code);
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "found";
        try {
            String longUrl = urlService.resolve(code);
            log.info("Request completed: GET /{} -> 302 {}", code, longUrl);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(longUrl))
                    .build();
        } catch (NotFoundException e) {
            outcome = "not_found";
            throw e;
        } catch (GoneException e) {
            outcome = "gone";
            throw e;
        } finally {
            sample.stop(Timer.builder("urlshortener.redirect.latency")
                    .description("Time to resolve a short code and issue the redirect")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
