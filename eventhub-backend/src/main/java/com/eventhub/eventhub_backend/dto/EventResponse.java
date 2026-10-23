package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EventStatus;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        Long organizerId,
        String title,
        String description,
        String venue,
        String city,
        String category,
        LocalDateTime startTime,
        LocalDateTime endTime,
        EventStatus status,
        String imageUrl
) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getOrganizer().getId(), e.getTitle(),
                e.getDescription(), e.getVenue(), e.getCity(), e.getCategory(),
                e.getStartTime(), e.getEndTime(), e.getStatus(), e.getImageUrl());
    }
}