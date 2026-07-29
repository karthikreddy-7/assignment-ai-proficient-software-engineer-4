package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.domain.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByCode(String code);

    /** Single atomic UPDATE so concurrent increments can't race (see ConcurrentClickAccountingTest). */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1, u.lastClickedAt = :now "
            + "WHERE u.code = :code")
    int incrementClickCount(@Param("code") String code, @Param("now") Instant now);
}
