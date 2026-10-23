package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.enums.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Size(max = 255) String venue,
        @Size(max = 255) String city,
        @Size(max = 255) String category,
        @NotNull LocalDateTime startTime,
        LocalDateTime endTime,
        @Size(max = 255) String imageUrl,
        EventStatus status
) {}