package com.eventhub.eventhub_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventDraftRequest(
        @NotBlank @Size(min = 10, max = 1500) String notes,
        @Size(max = 100) String city,
        @Size(max = 20) String tone
) {}