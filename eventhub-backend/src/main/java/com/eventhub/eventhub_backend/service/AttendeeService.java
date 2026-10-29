package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.AttendeeListResponse;
import com.eventhub.eventhub_backend.dto.AttendeeResponse;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendeeService {

    private final OwnedEventLookup ownedEventLookup;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public AttendeeListResponse list(Long eventId, Long organizerId) {
        Event event = ownedEventLookup.find(eventId, organizerId);

        // Only tickets from CONFIRMED bookings. Cancelled bookings don't count.
        List<AttendeeResponse> attendees = ticketRepository
                .findAttendees(eventId, BookingStatus.CONFIRMED)
                .stream()
                .map(AttendeeResponse::from)
                .toList();

        long checkedIn = attendees.stream().filter(AttendeeResponse::checkedIn).count();

        return new AttendeeListResponse(event.getId(), event.getTitle(), event.getStartTime(),
                attendees.size(), checkedIn, attendees);
    }
}