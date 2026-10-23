package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.TicketType;

import java.math.BigDecimal;

public record TicketTypeResponse(
        Long id,
        Long eventId,
        String name,
        BigDecimal price,
        Integer totalQuantity,
        Integer availableQuantity
) {
    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getEvent().getId(), t.getName(),
                t.getPrice(), t.getTotalQuantity(), t.getAvailableQuantity());
    }
}