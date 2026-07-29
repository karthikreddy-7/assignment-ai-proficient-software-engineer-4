package com.schwab.urlshortener.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.UUID;

/** X-API-Key required on POST/DELETE under /api/v1/urls; GET stays public. Stateless, no CSRF. */
@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/urls/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(this::writeUnauthorizedEnvelope));
        return http.build();
    }

    // Hand-written: filter-chain rejections never reach GlobalExceptionHandler.
    private void writeUnauthorizedEnvelope(jakarta.servlet.http.HttpServletRequest request,
                                            HttpServletResponse response,
                                            org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        log.warn("[{}] {} {} -> UNAUTHORIZED (missing/invalid X-API-Key)",
                traceId, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"error":"UNAUTHORIZED","message":"Missing or invalid X-API-Key","path":"%s","timestamp":"%s","traceId":"%s"}"""
                .formatted(request.getRequestURI(), Instant.now(), traceId));
    }
}
