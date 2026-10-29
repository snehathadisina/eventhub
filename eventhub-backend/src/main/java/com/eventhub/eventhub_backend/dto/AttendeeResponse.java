package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Ticket;

import java.time.LocalDateTime;

public record AttendeeResponse(
        Long ticketId,
        String ticketCode,
        String attendeeName,
        String attendeeEmail,
        String ticketTypeName,
        Long bookingId,
        boolean checkedIn,
        LocalDateTime checkedInAt
) {
    public static AttendeeResponse from(Ticket t) {
        Booking b = t.getBooking();
        return new AttendeeResponse(t.getId(), t.getTicketCode(),
                b.getUser().getName(), b.getUser().getEmail(),
                b.getTicketType().getName(), b.getId(),
                t.isCheckedIn(), t.getCheckedInAt());
    }
}