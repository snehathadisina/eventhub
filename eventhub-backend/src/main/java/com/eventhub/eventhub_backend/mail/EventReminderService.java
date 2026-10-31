package com.eventhub.eventhub_backend.mail;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventReminderService {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    /** @return true if a new reminder was queued for this booking */
    @Transactional
    public boolean remindIfDue(Long bookingId, LocalDateTime now, int hoursBefore) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }

        Event event = booking.getTicketType().getEvent();
        LocalDateTime start = event.getStartTime();
        if (event.getStatus() != EventStatus.PUBLISHED
                || !start.isAfter(now)
                || start.isAfter(now.plusHours(hoursBefore))) {
            return false;
        }

        // Booked inside the reminder window? The confirmation email is recent enough.
        if (!booking.getCreatedAt().isBefore(start.minusHours(hoursBefore))) {
            return false;
        }

        // Returns false if this booking was already reminded (dedupe key)
        return notificationService.eventReminder(booking);
    }
}