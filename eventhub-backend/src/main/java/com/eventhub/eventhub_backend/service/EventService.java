package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.EventRequest;
import com.eventhub.eventhub_backend.dto.EventResponse;
import com.eventhub.eventhub_backend.dto.TicketTypeRequest;
import com.eventhub.eventhub_backend.dto.TicketTypeResponse;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventResponse create(EventRequest request, Long organizerId) {
        validateTimes(request, true);
        Event event = new Event();
        event.setOrganizer(userRepository.getReferenceById(organizerId));
        applyRequest(event, request);
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse update(Long eventId, EventRequest request, Long organizerId) {
        Event event = getOwnedEvent(eventId, organizerId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled events cannot be edited");
        }
        validateTimes(request, false);
        applyRequest(event, request);
        return EventResponse.from(event);
    }

    @Transactional
    public void cancel(Long eventId, Long organizerId) {
        Event event = getOwnedEvent(eventId, organizerId);
        event.setStatus(EventStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listMine(Long organizerId) {
        return eventRepository.findByOrganizerIdOrderByStartTimeDesc(organizerId)
                .stream().map(EventResponse::from).toList();
    }

    @Transactional
    public TicketTypeResponse addTicketType(Long eventId, TicketTypeRequest request, Long organizerId) {
        Event event = getOwnedEvent(eventId, organizerId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot add tickets to a cancelled event");
        }
        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName(request.name().trim());
        ticketType.setPrice(request.price());
        ticketType.setTotalQuantity(request.totalQuantity());
        ticketType.setAvailableQuantity(request.totalQuantity());
        return TicketTypeResponse.from(ticketTypeRepository.save(ticketType));
    }

    @Transactional(readOnly = true)
    public List<TicketTypeResponse> listTicketTypes(Long eventId, Long organizerId) {
        getOwnedEvent(eventId, organizerId);
        return ticketTypeRepository.findByEventId(eventId)
                .stream().map(TicketTypeResponse::from).toList();
    }

    private Event getOwnedEvent(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own events");
        }
        return event;
    }

    private void validateTimes(EventRequest request, boolean isCreate) {
        if (isCreate && request.startTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be in the future");
        }
        if (request.endTime() != null && request.endTime().isBefore(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }

    @Transactional(readOnly = true)
    public EventResponse getMine(Long eventId, Long organizerId) {
        return EventResponse.from(getOwnedEvent(eventId, organizerId));
    }

    private void applyRequest(Event event, EventRequest request) {
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setCity(request.city());
        event.setCategory(request.category());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setImageUrl(request.imageUrl());
        if (request.status() != null) {
            if (request.status() != EventStatus.DRAFT && request.status() != EventStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Status can only be DRAFT or PUBLISHED. Use DELETE to cancel an event");
            }
            event.setStatus(request.status());
        }
    }
}