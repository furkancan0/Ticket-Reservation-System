package com.ticketing.controller;

import com.ticketing.dto.request.HoldRequest;
import com.ticketing.dto.response.SeatHoldResponse;
import com.ticketing.security.AuthenticatedUser;
import com.ticketing.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/holds")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatHoldService holdService;


    @PostMapping
    public ResponseEntity<SeatHoldResponse> holdSeat(
            @Valid @RequestBody HoldRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        SeatHoldResponse response = holdService.holdSeatIdempotent(req.getSeatId(), principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> releaseHold(
            @PathVariable String token,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        holdService.releaseHold(token, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
