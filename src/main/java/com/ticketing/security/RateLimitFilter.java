package com.ticketing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.ticketing.config.RateLimitConfig;
import com.ticketing.metrics.TicketingMetrics;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * FILTER ORDER AND PLACEMENT:
 * ────────────────────────────
 * Runs AFTER JwtAuthFilter so SecurityContext is already populated and
 * we know who the user is. Unauthenticated requests (login, register,
 * GET /events) are passed through without rate limiting — they are
 * protected by NGINX limits instead.
 *
 * RESPONSE ON LIMIT EXCEEDED:
 * ────────────────────────────
 * HTTP 429 Too Many Requests with:
 *   - Retry-After header (seconds until the bucket refills enough for 1 token)
 *   - X-Rate-Limit-Retry-After-Seconds (same value, for clients)
 *   - JSON body with a clear message
 *
 * WHICH BUCKET APPLIES:
 * ───────────────────────────────────────────────────────────────────
 *   POST /api/orders/checkout  → checkoutBucket (3/min)
 *   POST /api/holds            → holdBucket     (5/min)
 *   Everything else (auth'd)   → generalBucket  (60/min)
 */
@Slf4j
@Component
@Order(2)   // after JwtAuthFilter (order 1), before controllers
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> checkoutCache;
    private final Cache<String, Bucket> holdCache;
    private final Cache<String, Bucket> generalCache;
    private final ObjectMapper          objectMapper;
    private final TicketingMetrics      metrics;

    // Explicit constructor — @Qualifier works on constructor parameters,
    // but only when the constructor is written explicitly (not via Lombok).
    public RateLimitFilter(
            @Qualifier("checkoutBucketCache") Cache<String, Bucket> checkoutCache,
            @Qualifier("holdBucketCache")     Cache<String, Bucket> holdCache,
            @Qualifier("generalBucketCache")  Cache<String, Bucket> generalCache,
            ObjectMapper objectMapper,
            TicketingMetrics metrics) {
        this.checkoutCache = checkoutCache;
        this.holdCache     = holdCache;
        this.generalCache  = generalCache;
        this.objectMapper  = objectMapper;
        this.metrics       = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        // Only rate-limit authenticated requests — anonymous traffic handled by NGINX
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(req, res);
            return;
        }

        String userId = auth.getName();   // populated by JwtAuthFilter as the user UUID
        String path   = req.getRequestURI();
        String method = req.getMethod();

        // Pick the correct bucket for this endpoint
        BucketPolicy policy = resolvePolicy(method, path);
        Bucket bucket = getBucket(userId, policy);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Attach remaining token count to the response headers for debugging
            res.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, res);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            log.warn("Rate limit exceeded: user={} path={} policy={} retryAfter={}s",
                    userId, path, policy.name(), retryAfterSeconds);

            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setHeader("Retry-After",                        String.valueOf(retryAfterSeconds));
            res.setHeader("X-Rate-Limit-Retry-After-Seconds",  String.valueOf(retryAfterSeconds));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status",  429);
            body.put("error",   "Too Many Requests");
            body.put("message", policy.message());
            body.put("retryAfterSeconds", retryAfterSeconds);

            res.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BucketPolicy resolvePolicy(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && path.contains("/orders/checkout")) {
            return BucketPolicy.CHECKOUT;
        }
        if ("POST".equalsIgnoreCase(method) && path.contains("/holds")) {
            return BucketPolicy.HOLD;
        }
        return BucketPolicy.GENERAL;
    }

    private Bucket getBucket(String userId, BucketPolicy policy) {
        Cache<String, Bucket> cache = switch (policy) {
            case CHECKOUT -> checkoutCache;
            case HOLD     -> holdCache;
            case GENERAL  -> generalCache;
        };
        // computeIfAbsent: create a fresh bucket the first time a user hits this endpoint
        return cache.get(userId, key -> policy.createBucket());
    }

    // ── Policy enum ───────────────────────────────────────────────────────────

    enum BucketPolicy {
        CHECKOUT {
            @Override public Bucket createBucket() { return RateLimitConfig.buildCheckoutBucket(); }
            @Override public String  message()      { return "Checkout limit: 3 requests per minute per user."; }
        },
        HOLD {
            @Override public Bucket createBucket() { return RateLimitConfig.buildHoldBucket(); }
            @Override public String  message()      { return "Hold limit: 5 requests per minute per user."; }
        },
        GENERAL {
            @Override public Bucket createBucket() { return RateLimitConfig.buildGeneralBucket(); }
            @Override public String  message()      { return "API limit: 60 requests per minute per user."; }
        };

        public abstract Bucket createBucket();
        public abstract String  message();
    }
}
