package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Event;

import java.time.LocalDateTime;

public record PublicEventResponse(
        Long id,
        String title,
        String description,
        String venue,
        String city,
        String category,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String imageUrl
) {
    public static PublicEventResponse from(Event e) {
        return new PublicEventResponse(e.getId(), e.getTitle(), e.getDescription(),
                e.getVenue(), e.getCity(), e.getCategory(),
                e.getStartTime(), e.getEndTime(), e.getImageUrl());
    }
}