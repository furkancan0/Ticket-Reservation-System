package com.ticketing.exception;
public class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(String msg) { super(msg); }
}
