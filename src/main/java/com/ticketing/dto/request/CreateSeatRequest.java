package com.ticketing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;
@Data public class CreateSeatRequest {
    @NotNull  private UUID eventId;
    @NotBlank private String section;
    @NotBlank private String rowLabel;
    @NotBlank private String seatNum;
    @NotNull @DecimalMin("0.01") private BigDecimal price;
}
