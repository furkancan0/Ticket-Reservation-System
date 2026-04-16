package com.ticketing.payment;

import com.ticketing.domain.enums.PaymentProvider;

/**
 * Single Responsibility: defines only the payment contract.
 * Open/Closed: new providers are added by implementing this interface.
 */
public interface PaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
    PaymentProvider provider();
}
