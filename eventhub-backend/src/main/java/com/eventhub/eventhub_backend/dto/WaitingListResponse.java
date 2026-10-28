package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;

import java.time.LocalDateTime;

public record WaitingListResponse(
        Long id,
        Long eventId,
        String eventTitle,
        Long ticketTypeId,
        String ticketTypeName,
        Integer quantity,
        WaitingListStatus status,
        Long position,
        LocalDateTime createdAt
) {
    public static WaitingListResponse from(WaitingList w, Long position) {
        TicketType tt = w.getTicketType();
        Event e = tt.getEvent();
        return new WaitingListResponse(w.getId(), e.getId(), e.getTitle(), tt.getId(), tt.getName(),
                w.getQuantity(), w.getStatus(), position, w.getCreatedAt());
    }
}