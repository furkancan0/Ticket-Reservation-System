package com.ticketing.controller;

import com.ticketing.dto.response.EventResponse;
import com.ticketing.dto.response.SeatResponse;
import com.ticketing.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Browse events and their seating charts — no JWT required")
@SecurityRequirements
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "List active events")
    @ApiResponse(responseCode = "200", description = "List of active events",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EventResponse.class))))
    public ResponseEntity<List<EventResponse>> listActive() {
        return ResponseEntity.ok(eventService.getAllActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event found",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
    })
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(description = "Event UUID", example = "10000000-0000-0000-0000-000000000001")
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @GetMapping("/{id}/seats")
    @Operation(summary = "Seating chart",
            description = "Returns all seats for an event with their current status " +
                    "(AVAILABLE / PENDING / CONFIRMED). Refreshes every 15 s in the UI.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seat list",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeatResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
    })
    public ResponseEntity<List<SeatResponse>> seatingChart(
            @Parameter(description = "Event UUID", example = "10000000-0000-0000-0000-000000000001")
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(eventService.getSeatingChart(id));
    }
}
