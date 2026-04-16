package com.ticketing.exception;

import com.ticketing.metrics.TicketingMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TicketingMetrics metrics;


    @ExceptionHandler(SeatNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleSeatNotAvailable(SeatNotAvailableException ex) {
        metrics.recordException("SeatNotAvailableException");
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({HoldNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpired(HoldExpiredException ex) {
        return error(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(DiscountCodeInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleDiscount(DiscountCodeInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DuplicateHoldException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateHold(DuplicateHoldException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Two instances tried to modify the same seat row concurrently.
     * @Version caught the stale read — tell client to retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        metrics.recordException("OptimisticLockingFailureException");
        return error(HttpStatus.CONFLICT, "Seat updated concurrently. Please refresh and try again.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCreds(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "Validation failed: " + details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        metrics.recordException(ex.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        return ResponseEntity.status(status).body(body);
    }
}
