package com.ticketing.controller;

import com.ticketing.domain.entity.DiscountCode;
import com.ticketing.dto.request.CreateDiscountRequest;
import com.ticketing.dto.request.CreateEventRequest;
import com.ticketing.dto.request.CreateSeatRequest;
import com.ticketing.dto.response.EventResponse;
import com.ticketing.dto.response.OrderResponse;
import com.ticketing.dto.response.SeatResponse;
import com.ticketing.service.DiscountService;
import com.ticketing.service.EventService;
import com.ticketing.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Event, seat, and discount management — ADMIN role required")
public class AdminController {

    private final EventService    eventService;
    private final DiscountService discountService;
    private final OrderService    orderService;


    @PostMapping("/events")
    @Operation(summary = "Create event")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Event created",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "403", description = "Admin role required", content = @Content),
    })
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(req));
    }

    @PostMapping("/seats")
    @Operation(summary = "Add seat to event")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Seat added",
                    content = @Content(schema = @Schema(implementation = SeatResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
    })
    public ResponseEntity<SeatResponse> addSeat(
            @Valid @RequestBody CreateSeatRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.addSeat(req));
    }


    @PostMapping("/discounts")
    @Operation(summary = "Create discount code")
    @ApiResponse(responseCode = "201", description = "Discount code created",
            content = @Content(schema = @Schema(implementation = DiscountCode.class)))
    public ResponseEntity<DiscountCode> createDiscount(
            @Valid @RequestBody CreateDiscountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discountService.create(req));
    }

    @GetMapping("/discounts")
    @Operation(summary = "List all discount codes")
    @ApiResponse(responseCode = "200", description = "Discount code list",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DiscountCode.class))))
    public ResponseEntity<List<DiscountCode>> listDiscounts() {
        return ResponseEntity.ok(discountService.findAll());
    }

    @DeleteMapping("/discounts/{id}")
    @Operation(summary = "Deactivate discount code")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deactivated"),
            @ApiResponse(responseCode = "404", description = "Code not found", content = @Content),
    })
    public ResponseEntity<Void> deactivateDiscount(
            @Parameter(description = "Discount code UUID") @PathVariable("id") UUID id) {
        discountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/orders")
    @Operation(summary = "List all orders (all users)")
    @ApiResponse(responseCode = "200", description = "All orders",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class))))
    public ResponseEntity<List<OrderResponse>> allOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
