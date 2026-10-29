package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Duration;
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
        LocalDateTime expiresAt,
        Long secondsLeft,
        List<String> ticketCodes,
        List<TicketInfo> tickets
) {
    public static BookingResponse from(Booking b, List<Ticket> tickets) {
        TicketType tt = b.getTicketType();
        Event e = tt.getEvent();

        Long secondsLeft = null;
        if (b.getStatus() == BookingStatus.PENDING && b.getExpiresAt() != null) {
            secondsLeft = Math.max(0L, Duration.between(LocalDateTime.now(), b.getExpiresAt()).getSeconds());
        }

        return new BookingResponse(b.getId(), e.getId(), e.getTitle(), e.getStartTime(),
                tt.getId(), tt.getName(), b.getQuantity(), b.getStatus(),
                b.getTotalAmount(), b.getCreatedAt(), b.getExpiresAt(), secondsLeft,
                tickets.stream().map(Ticket::getTicketCode).toList(),
                tickets.stream().map(t -> new TicketInfo(t.getTicketCode(), t.isCheckedIn())).toList());
    }
}