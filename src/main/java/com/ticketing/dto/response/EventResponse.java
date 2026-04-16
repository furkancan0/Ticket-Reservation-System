package com.ticketing.dto.response;
import com.ticketing.domain.entity.Event;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Data @Builder
public class EventResponse {
    private UUID          id;
    private String        name;
    private String        description;
    private String        venue;
    private LocalDateTime eventDate;
    private boolean       active;

    public static EventResponse from(Event e) {
        return EventResponse.builder()
                .id(e.getId()).name(e.getName())
                .description(e.getDescription()).venue(e.getVenue())
                .eventDate(e.getEventDate()).active(e.isActive())
                .build();
    }
}
