package com.ticketing.service;

import com.ticketing.domain.entity.*;

import com.ticketing.domain.repository.*;
import com.ticketing.dto.request.CheckoutRequest;
import com.ticketing.dto.response.OrderResponse;
import com.ticketing.exception.*;
import com.ticketing.metrics.TicketingMetrics;
import com.ticketing.payment.*;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderTransactionService txService;
    private final OrderRepository         orderRepository;
    private final PaymentGatewayFactory   gatewayFactory;
    private final TicketingMetrics        metrics;
    private final Tracer                  tracer;

    @Observed(
            name            = "checkout",
            contextualName  = "checkout.flow",
            lowCardinalityKeyValues = {"component", "order-service"}
    )
    public OrderResponse checkout(CheckoutRequest req, UUID userId) {
        Span current = tracer.currentSpan();
        if (current != null) {
            current.tag("payment.provider",
                    req.getPaymentProvider() != null ? req.getPaymentProvider().name() : "unknown");
            current.tag("has.discount", String.valueOf(req.getDiscountCode() != null
                    && !req.getDiscountCode().isBlank()));
        }

        metrics.getCheckoutStartedCount().increment();

        Order order = txService.createPendingOrder(
                req.getHoldToken(), userId, req.getDiscountCode());

        order.setPaymentProvider(req.getPaymentProvider());
        orderRepository.save(order);

        PaymentResult result = attemptPayment(order, req);

        Order finalOrder = txService.finalizeOrder(
                order.getId(),
                req.getPaymentProvider() != null ? req.getPaymentProvider().name() : null,
                result);

        if (current != null) {
            current.tag("order.id",     finalOrder.getId().toString());
            current.tag("order.status", finalOrder.getStatus().name());
        }

        metrics.getCheckoutCompletedCount().increment();
        return OrderResponse.from(finalOrder);
    }

    @Observed(
            name            = "payment.attempt",
            contextualName  = "payment.gateway.call"
    )
    private PaymentResult attemptPayment(Order order, CheckoutRequest req) {
        long payStart = System.currentTimeMillis();

        Span current = tracer.currentSpan();
        if (current != null) {
            current.tag("payment.order.id", order.getId().toString());
            current.tag("payment.provider",
                    req.getPaymentProvider() != null ? req.getPaymentProvider().name() : "unknown");
            current.tag("payment.amount", order.getTotalAmount().toPlainString());
        }

        PaymentResult result;
        try {
            PaymentGateway gateway = gatewayFactory.getGateway(req.getPaymentProvider());
            result = gateway.processPayment(PaymentRequest.builder()
                    .orderId(order.getId())
                    .amount(order.getTotalAmount())
                    .currency("USD")
                    .paymentToken(req.getPaymentToken())
                    .provider(req.getPaymentProvider())
                    .customerEmail(order.getUser().getEmail())
                    .description("Ticket – " + order.getSeat().getEvent().getName()
                            + " seat " + order.getSeat().getSeatNum())
                    .build());

        } catch (PaymentDeclinedException ex) {
            log.warn("Hard decline for order {}: {}", order.getId(), ex.getMessage());
            result = PaymentResult.builder()
                    .success(false).status("DECLINED").errorMessage(ex.getMessage()).build();
        } catch (Exception ex) {
            log.error("Unexpected payment error for order {}", order.getId(), ex);
            result = PaymentResult.builder()
                    .success(false).status("ERROR").errorMessage(ex.getMessage()).build();
        }

        if (current != null) {
            current.tag("payment.result", result.getStatus());
        }

        String provider = req.getPaymentProvider() != null
                ? req.getPaymentProvider().name() : "unknown";
        metrics.recordPaymentDuration(provider, result.getStatus(),
                System.currentTimeMillis() - payStart);

        return result;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID requesterId, boolean isAdmin) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!isAdmin && !order.getUser().getId().equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied.");
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from).toList();
    }

}
