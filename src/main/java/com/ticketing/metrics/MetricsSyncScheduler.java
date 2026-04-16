package com.ticketing.metrics;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsSyncScheduler {

    private final JdbcTemplate     jdbc;
    private final TicketingMetrics  metrics;

    private volatile long activeHolds = 0L;

    @PostConstruct
    public void init() {
        syncActiveHolds();

        io.micrometer.core.instrument.Gauge
                .builder("seat.hold.active.count", this, MetricsSyncScheduler::getActiveHolds)
                .description("Currently active (non-expired) seat holds")
                .register(metrics.getRegistry());

        log.info("MetricsSyncScheduler initialized, active holds: {}", activeHolds);
    }

    @Scheduled(fixedDelayString = "15000")
    public void syncActiveHolds() {
        try {
            Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM seat_holds
                WHERE expired    = false
                  AND held_until > NOW()
                """, Long.class);
            activeHolds = count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Active holds sync failed: {}", ex.getMessage());
        }
    }

    public long getActiveHolds() {
        return activeHolds;
    }
}
