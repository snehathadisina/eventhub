package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

class TestData {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;

    TestData(UserRepository userRepository, EventRepository eventRepository,
             TicketTypeRepository ticketTypeRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
    }

    /** A published event 30 days away with one ticket type. Returns the ticket type id. */
    Long ticketType(int capacity) {
        User organizer = userRepository.save(newUser("Test Organizer", Role.ORGANIZER));

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle("Test Event");
        event.setStartTime(LocalDateTime.now().plusDays(30));
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);

        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName("General");
        ticketType.setPrice(new BigDecimal("100.00"));
        ticketType.setTotalQuantity(capacity);
        ticketType.setAvailableQuantity(capacity);
        return ticketTypeRepository.save(ticketType).getId();
    }

    Long buyer() {
        return userRepository.save(newUser("Buyer", Role.ATTENDEE)).getId();
    }

    /** Moves the event of this ticket type into the past. */
    void startEventInThePast(Long ticketTypeId) {
        Long eventId = ticketTypeRepository.findById(ticketTypeId).orElseThrow().getEvent().getId();
        Event event = eventRepository.findById(eventId).orElseThrow();
        event.setStartTime(LocalDateTime.now().minusDays(1));
        eventRepository.save(event);
    }

    private User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPasswordHash("not-used-in-this-test");
        user.setRole(role);
        return user;
    }
}