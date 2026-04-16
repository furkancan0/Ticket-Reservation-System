package com.ticketing.domain.repository;

import com.ticketing.domain.entity.DiscountCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {

    /**
     * Lock the discount code row before incrementing usedCount.
     * Prevents two concurrent orders from both reading usedCount = 9
     * and both seeing it as valid when maxUses = 10.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM DiscountCode d
            WHERE d.code       = :code
              AND d.active     = true
              AND d.usedCount  < d.maxUses
              AND d.validFrom  <= :now
              AND d.validUntil >= :now
           """)
    Optional<DiscountCode> findValidCodeWithLock(
            @Param("code") String code,
            @Param("now")  LocalDateTime now);
}
