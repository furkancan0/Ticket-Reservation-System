package com.ticketing.payment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResult {
    private boolean success;
    private String providerTransactionId;
    private String status;           // SUCCESS / FAILED / DECLINED / TIMEOUT
    private String errorMessage;
    private String rawResponse;
}
