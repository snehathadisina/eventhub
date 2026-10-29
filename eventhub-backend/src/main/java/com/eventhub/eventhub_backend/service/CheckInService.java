package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.CheckInResponse;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);

    private final OwnedEventLookup ownedEventLookup;
    private final TicketRepository ticketRepository;

    @Transactional
    public CheckInResponse checkIn(Long eventId, String ticketCode, Long organizerId) {
        Event event = ownedEventLookup.find(eventId, organizerId);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw conflict("This event is not open for check-in");
        }

        Ticket ticket = ticketRepository.findByCodeWithDetails(ticketCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        Booking booking = ticket.getBooking();

        // Same message whether the ticket belongs to another event of yours or of someone else
        if (!booking.getTicketType().getEvent().getId().equals(eventId)) {
            throw conflict("This ticket is for a different event");
        }
//        if (booking.getStatus() != BookingStatus.CONFIRMED) {
//            throw conflict("This ticket was cancelled");
//        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw conflict("This ticket has not been paid for yet");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw conflict("This ticket was cancelled");
        }
        if (ticket.isCheckedIn()) {
            throw conflict(alreadyUsedMessage(ticket));
        }

        LocalDateTime now = LocalDateTime.now();
        // The decisive step: one atomic statement, so two scanners can't both win
        if (ticketRepository.markCheckedIn(ticket.getId(), now, BookingStatus.CONFIRMED) == 0) {
            throw conflict("This ticket was just used or cancelled. Scan it again to see why");
        }

        long total = ticketRepository.countForEvent(eventId, BookingStatus.CONFIRMED);
        long checkedIn = ticketRepository.countCheckedInForEvent(eventId, BookingStatus.CONFIRMED);

        return new CheckInResponse(ticket.getTicketCode(), booking.getUser().getName(),
                booking.getTicketType().getName(), now, checkedIn, total);
    }

    private String alreadyUsedMessage(Ticket ticket) {
        if (ticket.getCheckedInAt() == null) {
            return "Already checked in";
        }
        return "Already checked in at " + TIME_FORMAT.format(ticket.getCheckedInAt());
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}