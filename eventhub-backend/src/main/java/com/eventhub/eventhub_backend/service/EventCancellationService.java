package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.mail.NotificationService;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.WaitingListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventCancellationService {

    private static final List<BookingStatus> ACTIVE = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final WaitingListRepository waitingListRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    /**
     * Cancels every active booking and waiting-list entry of the event,
     * refunds the paid ones, and queues an email for each person.
     * Runs inside the organizer's cancel transaction, so it all happens together or not at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelAllFor(Event event) {
        for (Long bookingId : bookingRepository.findIdsByEventAndStatusIn(event.getId(), ACTIVE)) {
            // Lock the row: a payment in progress finishes first, then we see its final state
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null || !ACTIVE.contains(booking.getStatus())) {
                continue;
            }

            BookingStatus previous = booking.getStatus();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setExpiresAt(null);

            if (previous == BookingStatus.CONFIRMED) {
                paymentService.refundIfPaid(booking); // a no-op for free tickets
            }
            notificationService.eventCancelled(booking);
        }

        for (WaitingList entry : waitingListRepository
                .findWaitingForEventForUpdate(event.getId(), WaitingListStatus.WAITING)) {
            entry.setStatus(WaitingListStatus.CANCELLED);
            notificationService.waitlistEventCancelled(entry);
        }
    }
}