package com.ticketing.dto.request;
import com.ticketing.domain.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data public class CreateDiscountRequest {
    @NotBlank private String code;
    @NotNull  private DiscountType discountType;
    @NotNull @DecimalMin("0.01") private BigDecimal value;
    @Min(1)   private int maxUses;
    @NotNull  private LocalDateTime validFrom;
    @NotNull  private LocalDateTime validUntil;
}
