package com.ticketing.payment;

import com.ticketing.domain.enums.PaymentProvider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class PaymentRequest {
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentToken;   // Stripe token / PayPal nonce.
    private PaymentProvider provider;
    private String customerEmail;
    private String description;
}
