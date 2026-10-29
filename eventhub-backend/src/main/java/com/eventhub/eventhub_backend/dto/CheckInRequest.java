package com.eventhub.eventhub_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @NotBlank @Size(max = 100) String ticketCode
) {}