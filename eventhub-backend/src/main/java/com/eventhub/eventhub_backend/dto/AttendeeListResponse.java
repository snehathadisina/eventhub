package com.eventhub.eventhub_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AttendeeListResponse(
        Long eventId,
        String eventTitle,
        LocalDateTime eventStartTime,
        long totalTickets,
        long checkedInCount,
        List<AttendeeResponse> attendees
) {}