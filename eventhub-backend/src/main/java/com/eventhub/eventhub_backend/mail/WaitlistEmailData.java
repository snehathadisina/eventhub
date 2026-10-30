package com.eventhub.eventhub_backend.mail;

import java.time.LocalDateTime;

public record WaitlistEmailData(
        Long entryId,
        String recipientName,
        String recipientEmail,
        Long eventId,
        String eventTitle,
        LocalDateTime eventStart,
        String ticketTypeName,
        int quantity,
        Long position
) {}