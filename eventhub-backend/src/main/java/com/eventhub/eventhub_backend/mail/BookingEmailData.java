package com.eventhub.eventhub_backend.mail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingEmailData(
        Long bookingId,
        String recipientName,
        String recipientEmail,
        Long eventId,
        String eventTitle,
        LocalDateTime eventStart,
        String venue,
        String city,
        String ticketTypeName,
        int quantity,
        BigDecimal total,
        List<String> ticketCodes,
        long minutesToPay
) {}