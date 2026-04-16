package com.ticketing.domain.enums;

public enum SeatStatus {
    AVAILABLE,
    PENDING,    // held, awaiting payment
    CONFIRMED   // paid and confirmed
}
