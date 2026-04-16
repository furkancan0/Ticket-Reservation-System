package com.ticketing.dto.response;
import com.ticketing.domain.entity.Seat;
import com.ticketing.domain.enums.SeatStatus;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
@Data @Builder
public class SeatResponse {
    private UUID       id;
    private UUID       eventId;
    private String     section;
    private String     rowLabel;
    private String     seatNum;
    private BigDecimal price;
    private SeatStatus status;

    public static SeatResponse from(Seat s) {
        return SeatResponse.builder()
                .id(s.getId())
                .eventId(s.getEvent().getId())
                .section(s.getSection())
                .rowLabel(s.getRowLabel())
                .seatNum(s.getSeatNum())
                .price(s.getPrice())
                .status(s.getStatus())
                .build();
    }
}
