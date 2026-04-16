package com.ticketing.domain.enums;

public enum OrderStatus {
    PENDING,          // created, awaiting payment
    CONFIRMED,        // payment successful
    PAYMENT_FAILED,   // payment attempted but failed
    CANCELLED,        // user or admin cancelled
    EXPIRED           // hold expired before payment was attempted
}
