package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        Long eventId,
        String eventTitle,
        LocalDateTime eventStartTime,
        Long ticketTypeId,
        String ticketTypeName,
        Integer quantity,
        BookingStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<String> ticketCodes
) {
    public static BookingResponse from(Booking b, List<Ticket> tickets) {
        TicketType tt = b.getTicketType();
        Event e = tt.getEvent();
        return new BookingResponse(b.getId(), e.getId(), e.getTitle(), e.getStartTime(),
                tt.getId(), tt.getName(), b.getQuantity(), b.getStatus(),
                b.getTotalAmount(), b.getCreatedAt(),
                tickets.stream().map(Ticket::getTicketCode).toList());
    }
}