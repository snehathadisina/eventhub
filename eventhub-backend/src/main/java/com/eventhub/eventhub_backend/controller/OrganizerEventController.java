package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.EventRequest;
import com.eventhub.eventhub_backend.dto.EventResponse;
import com.eventhub.eventhub_backend.dto.TicketTypeRequest;
import com.eventhub.eventhub_backend.dto.TicketTypeResponse;
import com.eventhub.eventhub_backend.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizer/events")
@RequiredArgsConstructor
public class OrganizerEventController {

    private final EventService eventService;

    @GetMapping
    public List<EventResponse> myEvents(@AuthenticationPrincipal Jwt jwt) {
        return eventService.listMine(organizerId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return eventService.create(request, organizerId(jwt));
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable Long id,
                                @Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return eventService.update(id, request, organizerId(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        eventService.cancel(id, organizerId(jwt));
    }

    @PostMapping("/{id}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse addTicketType(@PathVariable Long id,
                                            @Valid @RequestBody TicketTypeRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return eventService.addTicketType(id, request, organizerId(jwt));
    }

    @GetMapping("/{id}/ticket-types")
    public List<TicketTypeResponse> ticketTypes(@PathVariable Long id,
                                                @AuthenticationPrincipal Jwt jwt) {
        return eventService.listTicketTypes(id, organizerId(jwt));
    }

    @GetMapping("/{id}")
    public EventResponse getMine(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return eventService.getMine(id, organizerId(jwt));
    }

    private Long organizerId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}