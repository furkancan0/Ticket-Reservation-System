package com.ticketing.service;

import com.ticketing.domain.entity.Seat;
import com.ticketing.domain.entity.SeatHold;
import com.ticketing.domain.entity.User;
import com.ticketing.domain.enums.SeatStatus;
import com.ticketing.domain.repository.SeatHoldRepository;
import com.ticketing.domain.repository.SeatRepository;
import com.ticketing.domain.repository.UserRepository;
import com.ticketing.dto.response.SeatHoldResponse;
import com.ticketing.exception.DuplicateHoldException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.exception.SeatNotAvailableException;
import com.ticketing.metrics.TicketingMetrics;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private final SeatRepository       seatRepository;
    private final SeatHoldRepository   seatHoldRepository;
    private final UserRepository       userRepository;
    private final TicketingMetrics     metrics;
    private final Tracer             tracer;

    @Value("${ticketing.seat-hold.ttl-minutes:15}")
    private int holdTtlMinutes;

    @Observed(
            name            = "seat.hold",
            contextualName  = "seat.hold.acquire"
    )
    @Transactional
    public SeatHoldResponse holdSeat(UUID seatId, UUID userId) {
        // Idempotency: return existing active hold if present
        seatHoldRepository.findActiveHoldBySeatAndUser(seatId, userId, LocalDateTime.now())
                .ifPresent(existing -> {
                    throw new DuplicateHoldException("__EXISTING__:" + existing.getHoldToken());
                });
        Span current = tracer.currentSpan();

        long dbStart = System.currentTimeMillis();

        // double-sell prevention
        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));
        metrics.recordDbQueryDuration("find_seat_lock", System.currentTimeMillis() - dbStart);

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            if (current != null) {
                current.tag("hold.result", "seat_not_available");
                current.tag("seat.status", seat.getStatus().name());
            }
            throw new SeatNotAvailableException(
                    "Seat " + seatId + " is not available (status: " + seat.getStatus() + ")");
        }

        if (current != null) {
            current.tag("seat.id",      seatId.toString());
            current.tag("seat.section", seat.getSection());
            current.tag("seat.price",   seat.getPrice().toPlainString());
            current.tag("hold.result",  "acquired");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        seat.setStatus(SeatStatus.PENDING);

        dbStart = System.currentTimeMillis();
        seatRepository.save(seat);


        String holdToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime heldUntil = LocalDateTime.now().plusMinutes(holdTtlMinutes);

        SeatHold hold = SeatHold.builder()
                .seat(seat)
                .user(user)
                .holdToken(holdToken)
                .heldUntil(heldUntil)
                .build();
        dbStart = System.currentTimeMillis();
        seatHoldRepository.save(hold);

        metrics.recordDbQueryDuration("save_hold", System.currentTimeMillis() - dbStart);

        metrics.getSeatHoldCount().increment();

        log.info("Seat {} held by user {} until {} (token={})",
                seatId, userId, heldUntil, holdToken);

        return SeatHoldResponse.from(hold);
    }

    @Transactional
    public SeatHoldResponse holdSeatIdempotent(UUID seatId, UUID userId) {
        try {
            return holdSeat(seatId, userId);
        } catch (DuplicateHoldException ex) {
            String existingToken = ex.getMessage().replace("__EXISTING__:", "");
            return seatHoldRepository.findAll().stream()
                    .filter(h -> existingToken.equals(h.getHoldToken()))
                    .findFirst()
                    .map(SeatHoldResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Hold not found after idempotency check"));
        }
    }

    @Transactional
    public void releaseHold(String holdToken, UUID userId) {
        SeatHold hold = seatHoldRepository.findActiveByTokenWithLock(holdToken, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("Active hold not found: " + holdToken));

        if (!hold.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This hold does not belong to the requesting user.");
        }

        expireHold(hold);
        log.info("Hold {} released by user {}", holdToken, userId);
    }

    @Transactional
    public void expireHold(SeatHold hold) {
        hold.setExpired(true);
        seatHoldRepository.save(hold);

        Seat seat = seatRepository.findByIdWithLock(hold.getSeat().getId()).orElseThrow();
        if (seat.getStatus() == SeatStatus.PENDING) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seatRepository.save(seat);
        }
    }

    @Transactional(readOnly = true)
    public List<Seat> getAvailableSeats(UUID eventId) {
        return seatRepository.findByEventIdAndStatus(eventId, SeatStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public List<Seat> getAllSeats(UUID eventId) {
        return seatRepository.findByEventId(eventId);
    }
}
