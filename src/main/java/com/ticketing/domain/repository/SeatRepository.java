package com.ticketing.domain.repository;

import com.ticketing.domain.entity.Seat;
import com.ticketing.domain.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * When two app instances attempt to hold the same seat simultaneously,
     * only one will acquire this row-level lock.  The second blocks until
     * the first transaction commits or rolls back, then re-reads the now-
     * PENDING seat and throws SeatNotAvailableException.
     *
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") UUID id);

    List<Seat> findByEventIdAndStatus(UUID eventId, SeatStatus status);

    List<Seat> findByEventId(UUID eventId);

    @Query("SELECT s FROM Seat s JOIN FETCH s.event WHERE s.id = :id")
    Optional<Seat> findByIdWithEvent(@Param("id") UUID id);
}
