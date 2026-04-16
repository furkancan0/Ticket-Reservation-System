package com.ticketing.metrics;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

public class SlidingWindowRateTracker {

    private final long windowSeconds;
    private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
    private final LongAdder totalLifetime = new LongAdder();

    public SlidingWindowRateTracker(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    /** Record one event occurring right now. */
    public void record() {
        timestamps.addLast(Instant.now().toEpochMilli());
        totalLifetime.increment();
    }

    public double ratePerSecond() {
        evictExpired();
        long count = timestamps.size();
        return (double) count / windowSeconds;
    }

    public long countInWindow() {
        evictExpired();
        return timestamps.size();
    }

    public long totalCount() {
        return totalLifetime.sum();
    }

    private void evictExpired() {
        long cutoff = Instant.now().toEpochMilli() - (windowSeconds * 1_000);
        Long head;
        while ((head = timestamps.peekFirst()) != null && head < cutoff) {
            timestamps.pollFirst();
        }
    }
}
