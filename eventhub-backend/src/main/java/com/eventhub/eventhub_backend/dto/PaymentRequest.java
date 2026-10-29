package com.eventhub.eventhub_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Only the last 4 digits ever reach the server. The browser keeps the full card details.
public record PaymentRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String cardLast4
) {}