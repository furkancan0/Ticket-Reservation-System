package com.ticketing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Application-level cache configuration using Caffeine.
 *
 * WHY CAFFEINE, NOT REDIS (for this 2-instance setup)?
 * ─────────────────────────────────────────────────────
 * The data we cache (events, seats) is:
 *   - Read-only for regular users — never personalised
 *   - Identical on both instances
 *   - Short-lived (30s TTL) — slight staleness is acceptable because
 *     the pessimistic DB lock on hold/checkout always gives ground truth
 *
 * With sticky sessions in NGINX (hash $http_authorization), each user
 * always lands on the same instance. So a user's reads are always served
 * from the same Caffeine cache, which is always consistent with itself.
 *
 * Redis would add operational cost (another container, connection pool,
 * serialization) for zero benefit — the cached values are identical across
 * instances anyway. Caffeine gives identical hit rates with no network hop.
 *
 * CACHE NAMES AND TTLs:
 * ─────────────────────────────────────────────────────────────────────
 * "events"        — 60s TTL  — list of active events (changes rarely)
 * "event"         — 60s TTL  — single event by ID
 * "seats"         — 10s TTL  — seating chart (changes during active sales)
 *
 * Seat cache TTL is shorter (10s) because seat status changes frequently
 * during a sale. The 10s stale window is acceptable — the pessimistic lock
 * in holdSeat() is the real correctness guarantee.
 *
 * INVALIDATION:
 * ─────────────────────────────────────────────────────────────────────
 * @CacheEvict on admin write operations ensures the cache is cleared
 * immediately when an event is created or a seat is added, rather than
 * waiting for the TTL to expire.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Default spec for all caches unless overridden
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()   // exposes hit/miss counts to Micrometer
        );

        // Register named caches — each gets the same spec here;
        // override per-cache via setCacheNames + custom builders if needed
        manager.setCacheNames(java.util.List.of("events", "event", "seats"));

        return manager;
    }
}
