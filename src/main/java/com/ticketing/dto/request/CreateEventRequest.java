package com.ticketing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class CreateEventRequest {
    @NotBlank private String name;
    private String description;
    @NotBlank private String venue;
    @NotNull  private LocalDateTime eventDate;
}
