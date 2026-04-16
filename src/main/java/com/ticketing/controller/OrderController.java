package com.ticketing.controller;

import com.ticketing.dto.request.CheckoutRequest;
import com.ticketing.dto.response.OrderResponse;
import com.ticketing.security.AuthenticatedUser;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Checkout and order history")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @Operation(
            summary = "Complete checkout",
            description = """
            Completes the purchase for an active seat hold.
            
            Flow inside this endpoint:
            1. Validate hold token 
            2. Apply discount code if provided 
            3. Create order in `PENDING` status
            4. Call payment gateway (Stripe or PayPal) 
            5. Persist payment result and transition seat + order status
            
            **Always returns an order** — even on payment failure the order is
            persisted with status `PAYMENT_FAILED` so the full audit trail is preserved.
            The seat is released back to `AVAILABLE` on failure.
            
            **Discount codes**: `WELCOME10` (10% off), `FLAT5` ($5 off), `VIP50` (50% off)
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order CONFIRMED — payment successful",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "402", description = "Order PAYMENT_FAILED — payment declined/errored",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Hold not found", content = @Content),
            @ApiResponse(responseCode = "410", description = "Hold expired",    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid discount code", content = @Content),
    })
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        OrderResponse order  = orderService.checkout(req, principal.getId());
        HttpStatus    status = switch (order.getStatus()) {
            case CONFIRMED      -> HttpStatus.CREATED;
            case PAYMENT_FAILED -> HttpStatus.PAYMENT_REQUIRED;
            default             -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/me")
    @Operation(summary = "My order history",
            description = "Returns all orders placed by the authenticated user, newest first.")
    @ApiResponse(responseCode = "200", description = "Order list",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class))))
    public ResponseEntity<List<OrderResponse>> myOrders(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(orderService.getMyOrders(principal.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID",
            description = "Regular users may only fetch their own orders. Admins may fetch any order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "Order belongs to another user", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content),
    })
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order UUID") @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdmin = "ADMIN".equals(principal.getRole());
        return ResponseEntity.ok(orderService.getOrder(id, principal.getId(), isAdmin));
    }
}
