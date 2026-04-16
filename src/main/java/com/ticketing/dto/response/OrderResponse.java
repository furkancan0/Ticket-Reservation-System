package com.ticketing.dto.response;
import com.ticketing.domain.entity.Order;
import com.ticketing.domain.enums.OrderStatus;
import com.ticketing.domain.enums.PaymentProvider;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Data @Builder
public class OrderResponse {
    private UUID            id;
    private UUID            userId;
    private UUID            seatId;
    private String          seatLabel;
    private OrderStatus     status;
    private BigDecimal      originalAmount;
    private BigDecimal      discountAmount;
    private BigDecimal      totalAmount;
    private PaymentProvider paymentProvider;
    private String          externalPaymentId;
    private String          failureReason;
    private LocalDateTime   createdAt;

    public static OrderResponse from(Order o) {
        String label = o.getSeat().getSection() + "-"
                + o.getSeat().getRowLabel() + "-"
                + o.getSeat().getSeatNum();
        return OrderResponse.builder()
                .id(o.getId())
                .userId(o.getUser().getId())
                .seatId(o.getSeat().getId())
                .seatLabel(label)
                .status(o.getStatus())
                .originalAmount(o.getOriginalAmount())
                .discountAmount(o.getDiscountAmount())
                .totalAmount(o.getTotalAmount())
                .paymentProvider(o.getPaymentProvider())
                .externalPaymentId(o.getExternalPaymentId())
                .failureReason(o.getFailureReason())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
