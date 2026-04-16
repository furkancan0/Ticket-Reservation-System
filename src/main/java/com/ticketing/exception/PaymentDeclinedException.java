package com.ticketing.exception;
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String msg) { super(msg); }
}
