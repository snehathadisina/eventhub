package com.eventhub.eventhub_backend.dto;

import java.time.LocalDateTime;

public record CheckInResponse(
        String ticketCode,
        String attendeeName,
        String ticketTypeName,
        LocalDateTime checkedInAt,
        long checkedInCount,
        long totalTickets
) {}