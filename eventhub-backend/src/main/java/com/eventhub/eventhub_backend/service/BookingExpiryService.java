package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.mail.NotificationService;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final WaitingListPromotionService waitingListPromotionService;
    private final NotificationService notificationService;

    /**
     * Cancels the booking if it is still unpaid and its time is up, puts the tickets back on sale,
     * and offers them to the next people waiting.
     *
     * @return true if this call released the booking
     */
    @Transactional
    public boolean expireIfOverdue(Long bookingId) {
        // If someone is paying right now we wait for them, then see the final status
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) {
            return false; // paid or cancelled in the meantime
        }
        if (booking.getExpiresAt() == null || booking.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false; // still inside its payment window
        }

        Long ticketTypeId = booking.getTicketType().getId();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);

        if (ticketTypeRepository.incrementStock(ticketTypeId, booking.getQuantity()) == 0) {
            throw new IllegalStateException("Could not restore stock for booking " + bookingId);
        }

//        waitingListPromotionService.promoteFor(ticketTypeId);
//        return true;
        notificationService.bookingExpired(booking);

        waitingListPromotionService.promoteFor(ticketTypeId);
        return true;
    }
}