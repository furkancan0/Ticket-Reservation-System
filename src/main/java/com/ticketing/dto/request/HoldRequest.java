package com.ticketing.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;
@Data public class HoldRequest {
    @NotNull private UUID seatId;
}
