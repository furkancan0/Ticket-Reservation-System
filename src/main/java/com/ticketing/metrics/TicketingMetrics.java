package com.ticketing.metrics;

import io.micrometer.core.instrument.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Getter
public class TicketingMetrics {

    private final Counter ticketPurchaseSuccessCount;
    private final Counter ticketPurchaseFailedCount;
    private final Counter checkoutStartedCount;
    private final Counter checkoutCompletedCount;
    private final Counter seatHoldCount;
    private final Counter seatHoldExpiredCount;
    private final Counter seatPurchaseCount;

    private final MeterRegistry registry;

    public TicketingMetrics(MeterRegistry registry) {
        this.registry = registry;

        ticketPurchaseSuccessCount = Counter.builder("ticket.purchase.success.count")
                .description("Tickets successfully purchased")
                .register(registry);

        ticketPurchaseFailedCount = Counter.builder("ticket.purchase.failed.count")
                .description("Ticket purchases that failed (payment declined/error)")
                .register(registry);

        checkoutStartedCount = Counter.builder("checkout.started.count")
                .description("Checkout flows started (hold validated, PENDING order created)")
                .register(registry);

        checkoutCompletedCount = Counter.builder("checkout.completed.count")
                .description("Checkout flows completed — confirmed or failed, not abandoned")
                .register(registry);

        seatHoldCount = Counter.builder("seat.hold.count")
                .description("Seat holds created")
                .register(registry);

        seatHoldExpiredCount = Counter.builder("seat.hold.expired.count")
                .description("Seat holds expired by the background scheduler")
                .register(registry);

        seatPurchaseCount = Counter.builder("seat.purchase.count")
                .description("Seats confirmed (purchased and paid)")
                .register(registry);

        log.info("TicketingMetrics initialized");
    }

    // payment.duration
    public void recordPaymentDuration(String provider, String result, long durationMs) {
        Timer.builder("payment.duration")
                .description("Time spent waiting for payment gateway response")
                .tag("provider", lower(provider, "unknown"))
                .tag("result",   lower(result,   "unknown"))
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    // db.query.duration
    public void recordDbQueryDuration(String operation, long durationMs) {
        Timer.builder("db.query.duration")
                .description("Database query / repository operation duration")
                .tag("operation", operation)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    // exception.count
    public void recordException(String type) {
        Counter.builder("exception.count")
                .description("Handled exceptions by type")
                .tag("type", type != null ? type : "Unknown")
                .register(registry)
                .increment();
    }

    //  Helpers
    private String lower(String value, String fallback) {
        return value != null ? value.toLowerCase() : fallback;
    }
}
