package com.ticketing.scheduler;

import com.ticketing.domain.entity.SeatHold;
import com.ticketing.domain.repository.SeatHoldRepository;
import com.ticketing.metrics.TicketingMetrics;
import com.ticketing.service.SeatHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldService    seatHoldService;
    private final TicketingMetrics   metrics;

    @Scheduled(fixedDelayString = "${ticketing.scheduler.expiry-interval-ms:30000}")
    @SchedulerLock(name = "holdExpiryJob", lockAtMostFor = "PT1M", lockAtLeastFor = "PT25S")
    @Transactional
    public void expireStaleHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<SeatHold> stale = seatHoldRepository.findExpiredHolds(now);

        if (stale.isEmpty()) {
            return;
        }

        int expired = 0, errors = 0;
        for (SeatHold hold : stale) {
            try {
                seatHoldService.expireHold(hold);

                // ── seat.hold.expired.count ───────────────────────
                metrics.getSeatHoldExpiredCount().increment();
                expired++;
            } catch (Exception ex) {
                errors++;
            }
        }

        int bulk = seatHoldRepository.bulkExpireHolds(now);
    }
}
