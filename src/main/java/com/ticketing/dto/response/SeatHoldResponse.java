package com.ticketing.dto.response;
import com.ticketing.domain.entity.SeatHold;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Data @Builder
public class SeatHoldResponse {
    private UUID          holdId;
    private String        holdToken;
    private UUID          seatId;
    private String        section;
    private String        rowLabel;
    private String        seatNum;
    private LocalDateTime heldUntil;
    private boolean       expired;

    public static SeatHoldResponse from(SeatHold h) {
        return SeatHoldResponse.builder()
                .holdId(h.getId())
                .holdToken(h.getHoldToken())
                .seatId(h.getSeat().getId())
                .section(h.getSeat().getSection())
                .rowLabel(h.getSeat().getRowLabel())
                .seatNum(h.getSeat().getSeatNum())
                .heldUntil(h.getHeldUntil())
                .expired(h.isExpired())
                .build();
    }
}
