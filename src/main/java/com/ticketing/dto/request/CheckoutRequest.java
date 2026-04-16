package com.ticketing.dto.request;
import com.ticketing.domain.enums.PaymentProvider;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class CheckoutRequest {
    @NotBlank private String holdToken;
    @NotNull  private PaymentProvider paymentProvider;
    @NotBlank private String paymentToken;
    private String discountCode;
}
