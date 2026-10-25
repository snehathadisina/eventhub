package com.eventhub.eventhub_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull Long ticketTypeId,
        @NotNull @Min(1) @Max(10) Integer quantity
) {}