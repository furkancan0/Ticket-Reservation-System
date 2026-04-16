package com.ticketing.exception;
public class PaymentRetryableException extends RuntimeException {
    public PaymentRetryableException(String msg, Throwable cause) { super(msg, cause); }
}
