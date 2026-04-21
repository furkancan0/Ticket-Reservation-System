package com.ticketing.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 *
 * WHY APPLICATION-LAYER RATE LIMITING IN ADDITION TO NGINX?
 * ─────────────────────────────────────────────────────────
 * NGINX limits by source IP. From a single machine (k6, office NAT,
 * shared WiFi) all users share the same IP and compete for the same bucket.
 * A single user making 200 requests/s can exhaust the quota for everyone
 * behind that IP.
 *
 * Bucket4j limits by authenticated USER ID. Each user gets their own
 * independent token bucket regardless of which IP they're coming from.
 * This means:
 *   - Legitimate users behind a shared IP are never blocked by a bad actor
 *   - A logged-in user cannot abuse the API by rotating their IP
 *   - Rate limit enforcement survives horizontal scaling with Redis backend
 *
 * TOKEN BUCKET ALGORITHM:
 * ───────────────────────
 * Each bucket has a capacity and refills at a fixed rate.
 * When a request arrives, one token is consumed.
 * If no tokens are available → 429 Too Many Requests.
 * Tokens accumulate (up to capacity) when no requests are made.
 *
 * This naturally handles bursts: a user who hasn't used the API for a
 * while can fire several requests in quick succession, but cannot
 * sustain a high rate indefinitely.
 *
 * POLICIES:
 * ─────────────────────────────────────────────────────────────────
 * CHECKOUT  — 3 requests per minute
 *   Rationale: a user buys one ticket at a time. 3/min allows:
 *   - 1 attempt that fails + 1 retry + 1 final attempt per minute.
 *   - Prevents programmatic ticket scooping with rapid checkouts.
 *
 * HOLD      — 5 holds per minute
 *   Rationale: a user browses several seats before committing.
 *   5 allows comparison shopping without enabling bulk holding.
 *
 * GENERAL   — 60 requests per minute
 *   Rationale: standard API browsing limit. 1 req/sec is enough for
 *   any legitimate use case (listing events, viewing seat charts).
 */
@Configuration
public class RateLimitConfig {

    // ── Bucket policies ────────────────────────────────────────────────────

    /**
     * Checkout: 3 requests per minute.
     * Refills completely every 60 seconds.
     */
    public static Bucket buildCheckoutBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillGreedy(3, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Seat hold: 5 requests per minute.
     */
    public static Bucket buildHoldBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * General authenticated endpoints: 60 requests per minute.
     */
    public static Bucket buildGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(60)
                        .refillGreedy(60, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    // ── Per-user bucket caches (Caffeine) ──────────────────────────────────
    //
    // Each user gets their own Bucket instance stored in a Caffeine cache.
    // Caffeine evicts idle entries after 1 hour — a user who hasn't made
    // a request for an hour gets a fresh bucket (effectively resetting their
    // limit), which is the correct UX.
    //
    // For multi-instance deployments, replace these with Bucket4j Redis
    // backends so all instances share the same per-user token count:
    //   io.github.bucket4j:bucket4j-redis + Spring Data Redis

    @Bean("checkoutBucketCache")
    public Cache<String, Bucket> checkoutBucketCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)           // holds up to 10k active users
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    @Bean("holdBucketCache")
    public Cache<String, Bucket> holdBucketCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    @Bean("generalBucketCache")
    public Cache<String, Bucket> generalBucketCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }
}
