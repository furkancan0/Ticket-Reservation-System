package com.ticketing.service;

import com.ticketing.domain.entity.*;
import com.ticketing.domain.enums.DiscountType;
import com.ticketing.domain.enums.OrderStatus;
import com.ticketing.domain.enums.SeatStatus;
import com.ticketing.domain.repository.*;
import com.ticketing.exception.*;
import com.ticketing.domain.entity.SeatHold;
import com.ticketing.metrics.TicketingMetrics;
import com.ticketing.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final SeatHoldRepository           seatHoldRepository;
    private final SeatRepository               seatRepository;
    private final OrderRepository              orderRepository;
    private final DiscountCodeRepository       discountCodeRepository;
    private final PaymentTransactionRepository paymentTxRepository;
    private final TicketingMetrics             metrics;

    // Phase 1: hold + discount → PENDING order

    @Transactional(rollbackFor = Exception.class)
    public Order createPendingOrder(String holdToken, UUID userId, String discountCode) {

        long dbStart = System.currentTimeMillis();
        SeatHold hold = seatHoldRepository
                .findActiveByTokenWithLock(holdToken, LocalDateTime.now())
                .orElseThrow(() -> new HoldNotFoundException(
                        "Hold not found or already expired: " + holdToken));
        metrics.recordDbQueryDuration("find_active_hold", System.currentTimeMillis() - dbStart);

        if (!hold.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Hold does not belong to this user.");
        }

        Seat seat            = hold.getSeat();
        BigDecimal original  = seat.getPrice();
        BigDecimal discount  = BigDecimal.ZERO;
        DiscountCode code    = null;

        if (discountCode != null && !discountCode.isBlank()) {
            code = discountCodeRepository
                    .findValidCodeWithLock(discountCode, LocalDateTime.now())
                    .orElseThrow(() -> new DiscountCodeInvalidException(
                            "Discount code invalid/expired/fully used: " + discountCode));

            discount = calculateDiscount(original, code);
            code.setUsedCount(code.getUsedCount() + 1);
            discountCodeRepository.save(code);
        }

        BigDecimal total = original.subtract(discount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .user(hold.getUser()).seat(seat).discountCode(code)
                .status(OrderStatus.PENDING)
                .originalAmount(original).discountAmount(discount).totalAmount(total)
                .build();

        dbStart = System.currentTimeMillis();
        Order saved = orderRepository.save(order);
        metrics.recordDbQueryDuration("save_order", System.currentTimeMillis() - dbStart);

        // Success path: seat becomes CONFIRMED — hold is irrelevant
        // Failure path: seat returns to AVAILABLE — user must create a NEW hold
        hold.setExpired(true);
        seatHoldRepository.save(hold);

        log.info("Order {} PENDING seat={} total={}", saved.getId(), seat.getId(), total);
        return saved;
    }

    // Phase 2: finalize — update order + seat

    @Transactional(rollbackFor = Exception.class)
    public Order finalizeOrder(UUID orderId, String providerName, PaymentResult result) {

        long dbStart = System.currentTimeMillis();
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        metrics.recordDbQueryDuration("find_order_details", System.currentTimeMillis() - dbStart);

        Seat seat = seatRepository.findByIdWithLock(order.getSeat().getId()).orElseThrow();

        com.ticketing.domain.enums.PaymentProvider provider = null;
        try { provider = com.ticketing.domain.enums.PaymentProvider.valueOf(providerName); }
        catch (Exception ignored) {}

        paymentTxRepository.save(PaymentTransaction.builder()
                .order(order).provider(provider)
                .providerTransactionId(result.getProviderTransactionId())
                .amount(order.getTotalAmount())
                .status(result.getStatus())
                .errorMessage(result.getErrorMessage())
                .rawResponse(result.getRawResponse())
                .build());

        if (result.isSuccess()) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setExternalPaymentId(result.getProviderTransactionId());
            seat.setStatus(SeatStatus.CONFIRMED);

            metrics.getTicketPurchaseSuccessCount().increment();
            metrics.getSeatPurchaseCount().increment();

            log.info("Order {} CONFIRMED seat {}", orderId, seat.getId());
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailureReason(result.getStatus() + ": " + result.getErrorMessage());
            seat.setStatus(SeatStatus.AVAILABLE);

            metrics.getTicketPurchaseFailedCount().increment();

            log.warn("Order {} PAYMENT_FAILED ({}) seat {} released",
                    orderId, result.getStatus(), seat.getId());
        }

        dbStart = System.currentTimeMillis();
        seatRepository.save(seat);
        Order saved = orderRepository.save(order);
        metrics.recordDbQueryDuration("save_order_final", System.currentTimeMillis() - dbStart);

        return saved;
    }

    private BigDecimal calculateDiscount(BigDecimal price, DiscountCode code) {
        if (code.getDiscountType() == DiscountType.PERCENTAGE) {
            return price.multiply(code.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return code.getValue().min(price).setScale(2, RoundingMode.HALF_UP);
    }
}
