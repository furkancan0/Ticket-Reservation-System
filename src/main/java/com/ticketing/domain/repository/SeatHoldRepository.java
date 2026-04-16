package com.ticketing.domain.repository;

import com.ticketing.domain.entity.SeatHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    /**
     * Fetch + lock the hold row before confirming a purchase.
     * Prevents the expiry scheduler from expiring this hold
     * at the exact same moment as checkout completes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h FROM SeatHold h
            JOIN FETCH h.seat
            WHERE h.holdToken = :token
              AND h.expired   = false
              AND h.heldUntil > :now
           """)
    Optional<SeatHold> findActiveByTokenWithLock(
            @Param("token") String token,
            @Param("now")   LocalDateTime now);

    @Query("""
            SELECT h FROM SeatHold h
            JOIN FETCH h.seat
            WHERE h.expired   = false
              AND h.heldUntil <= :now
           """)
    List<SeatHold> findExpiredHolds(@Param("now") LocalDateTime now);

    @Query("""
            SELECT h FROM SeatHold h
            WHERE h.seat.id  = :seatId
              AND h.user.id  = :userId
              AND h.expired  = false
              AND h.heldUntil > :now
           """)
    Optional<SeatHold> findActiveHoldBySeatAndUser(
            @Param("seatId") UUID seatId,
            @Param("userId") UUID userId,
            @Param("now")    LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE SeatHold h
            SET h.expired = true
            WHERE h.expired   = false
              AND h.heldUntil <= :now
           """)
    int bulkExpireHolds(@Param("now") LocalDateTime now);
}
