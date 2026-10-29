package com.eventhub.eventhub_backend.dto;

public record PaymentResponse(
        boolean success,
        String message,
        String reference,
        BookingResponse booking
) {}