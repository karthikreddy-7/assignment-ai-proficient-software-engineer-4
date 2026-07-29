package com.schwab.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "url_mapping")
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Transiently null between insert and code-assignment in UrlService.shorten().
    @Setter
    @Column(name = "code", unique = true, length = 10)
    private String code;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Setter
    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Setter
    @Column(name = "last_clicked_at")
    private Instant lastClickedAt;

    protected UrlMapping() {
        // JPA
    }

    public UrlMapping(String code, String longUrl, Instant expiresAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.clickCount = 0;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public boolean isDisabled() {
        return disabledAt != null;
    }

    public void disable() {
        this.disabledAt = Instant.now();
    }
}
