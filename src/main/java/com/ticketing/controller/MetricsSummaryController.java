package com.ticketing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Metrics", description = "Real-time business metrics summary — ADMIN only. Raw data at /actuator/prometheus")
public class MetricsSummaryController {

    private final io.micrometer.core.instrument.MeterRegistry registry;

    @GetMapping("/summary")
    @Operation(
            summary = "Business metrics snapshot",
            description = """
            Live counters from the Micrometer registry.
            """
    )
    public ResponseEntity<Map<String, Object>> summary() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT));

        // ── Purchases ─────────────────────────────────────────────
        Map<String, Object> purchases = new LinkedHashMap<>();
        purchases.put("success_total",  counterValue("ticket.purchase.success.count"));
        purchases.put("failed_total",   counterValue("ticket.purchase.failed.count"));
        purchases.put("seat_purchased", counterValue("seat.purchase.count"));
        body.put("purchases", purchases);

        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("started_total",   counterValue("checkout.started.count"));
        checkout.put("completed_total", counterValue("checkout.completed.count"));
        double started   = counterValue("checkout.started.count");
        double completed = counterValue("checkout.completed.count");
        checkout.put("completion_rate_pct",
                started > 0 ? Math.round(completed / started * 100 * 10.0) / 10.0 : 0.0);
        body.put("checkout_funnel", checkout);

        Map<String, Object> holds = new LinkedHashMap<>();
        holds.put("created_total",  counterValue("seat.hold.count"));
        holds.put("expired_total",  counterValue("seat.hold.expired.count"));
        body.put("holds", holds);

        body.put("exceptions_total", counterValue("exception.count"));

        body.put("_tip", "For today/week totals use Grafana or PromQL: " +
                "increase(ticket_purchase_success_count_total[24h])");

        return ResponseEntity.ok(body);
    }

    private double counterValue(String name) {
        try {
            return registry.find(name).counters().stream()
                    .mapToDouble(io.micrometer.core.instrument.Counter::count)
                    .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
