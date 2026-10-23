package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Event;

import java.time.LocalDateTime;
import java.util.List;

public record EventDetailResponse(
        Long id,
        String title,
        String description,
        String venue,
        String city,
        String category,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String imageUrl,
        String organizerName,
        List<TicketTypeResponse> ticketTypes
) {
    public static EventDetailResponse from(Event e, List<TicketTypeResponse> ticketTypes) {
        return new EventDetailResponse(e.getId(), e.getTitle(), e.getDescription(),
                e.getVenue(), e.getCity(), e.getCategory(),
                e.getStartTime(), e.getEndTime(), e.getImageUrl(),
                e.getOrganizer().getName(), ticketTypes);
    }
}