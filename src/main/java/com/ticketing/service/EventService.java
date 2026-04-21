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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository  seatRepository;

    @Cacheable(value = "events", key = "'all'")
    @Transactional(readOnly = true)
    public List<EventResponse> getAllActive() {
        return eventRepository.findByActiveTrue()
                .stream().map(EventResponse::from).toList();
    }

    @Cacheable(value = "event", key = "#id")
    @Transactional(readOnly = true)
    public EventResponse getById(UUID id) {
        return EventResponse.from(findOrThrow(id));
    }

    @Cacheable(value = "seats", key = "#eventId")
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatingChart(UUID eventId) {
        log.debug("Cache MISS — loading seats for event {} from DB", eventId);
        findOrThrow(eventId);
        return seatRepository.findByEventId(eventId)
                .stream().map(SeatResponse::from).toList();
    }

    /**
     * Evicts "events" list and "seats" caches on create.
     * The new event must be immediately visible — not after TTL expiry.
     */
    @Caching(evict = {
            @CacheEvict(value = "events", key = "'all'"),
    })
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

    /**
     * Evicts the seats cache for this event when a seat is added.
     * Also evicts the event cache in case seat count is included in the response.
     */
    @Caching(evict = {
            @CacheEvict(value = "seats", key = "#req.eventId"),
            @CacheEvict(value = "event", key = "#req.eventId"),
    })
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

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }
}
