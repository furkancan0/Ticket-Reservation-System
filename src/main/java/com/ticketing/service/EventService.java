package com.ticketing.service;

import com.ticketing.domain.entity.Event;
import com.ticketing.domain.entity.Seat;
import com.ticketing.domain.repository.EventRepository;
import com.ticketing.domain.repository.SeatRepository;
import com.ticketing.dto.request.CreateEventRequest;
import com.ticketing.dto.request.CreateSeatRequest;
import com.ticketing.dto.response.EventResponse;
import com.ticketing.dto.response.SeatResponse;
import com.ticketing.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository  seatRepository;

    @Transactional(readOnly = true)
    public List<EventResponse> getAllActive() {
        return eventRepository.findByActiveTrue()
                .stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID id) {
        return EventResponse.from(findOrThrow(id));
    }

    @Transactional
    public EventResponse create(CreateEventRequest req) {
        Event e = Event.builder()
                .name(req.getName())
                .description(req.getDescription())
                .venue(req.getVenue())
                .eventDate(req.getEventDate())
                .build();
        return EventResponse.from(eventRepository.save(e));
    }

    @Transactional
    public SeatResponse addSeat(CreateSeatRequest req) {
        Event event = findOrThrow(req.getEventId());
        Seat seat = Seat.builder()
                .event(event)
                .section(req.getSection())
                .rowLabel(req.getRowLabel())
                .seatNum(req.getSeatNum())
                .price(req.getPrice())
                .build();
        return SeatResponse.from(seatRepository.save(seat));
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatingChart(UUID eventId) {
        findOrThrow(eventId);
        return seatRepository.findByEventId(eventId)
                .stream().map(SeatResponse::from).toList();
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }
}
