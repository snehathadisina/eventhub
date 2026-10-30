package com.eventhub.eventhub_backend.mail;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.EmailType;
import com.eventhub.eventhub_backend.enums.PaymentStatus;
import com.eventhub.eventhub_backend.repository.PaymentRepository;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every method must run inside the transaction of the thing that happened,
 * so the email is saved or discarded together with the booking, payment or cancellation.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailOutboxService outbox;
    private final EmailTemplates templates;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingConfirmed(Booking booking, boolean fromWaitingList) {
        BookingEmailData data = bookingData(booking, true);
        outbox.enqueue(EmailType.BOOKING_CONFIRMED, data.recipientEmail(),
                templates.bookingConfirmed(data, fromWaitingList), "CONFIRMED:booking:" + booking.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ticketsHeld(Booking booking) {
        BookingEmailData data = bookingData(booking, false);
        outbox.enqueue(EmailType.TICKETS_HELD, data.recipientEmail(),
                templates.ticketsHeld(data), "HELD:booking:" + booking.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCancelled(Booking booking) {
        BookingEmailData data = bookingData(booking, false);
        boolean refunded = wasRefunded(booking);
        outbox.enqueue(EmailType.BOOKING_CANCELLED, data.recipientEmail(),
                templates.bookingCancelled(data, refunded), "CANCELLED:booking:" + booking.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingExpired(Booking booking) {
        BookingEmailData data = bookingData(booking, false);
        outbox.enqueue(EmailType.BOOKING_EXPIRED, data.recipientEmail(),
                templates.bookingExpired(data), "EXPIRED:booking:" + booking.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void eventCancelled(Booking booking) {
        BookingEmailData data = bookingData(booking, false);
        boolean refunded = wasRefunded(booking);
        outbox.enqueue(EmailType.EVENT_CANCELLED, data.recipientEmail(),
                templates.eventCancelled(data, refunded), "EVENT_CANCELLED:booking:" + booking.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void waitlistEventCancelled(WaitingList entry) {
        WaitlistEmailData data = waitlistData(entry, null);
        outbox.enqueue(EmailType.EVENT_CANCELLED, data.recipientEmail(),
                templates.eventCancelledForWaitingList(data), "EVENT_CANCELLED:waitlist:" + entry.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void waitlistJoined(WaitingList entry, Long position) {
        WaitlistEmailData data = waitlistData(entry, position);
        outbox.enqueue(EmailType.WAITLIST_JOINED, data.recipientEmail(),
                templates.waitlistJoined(data), "WAITLIST:entry:" + entry.getId());
    }

    /** @return true if a new reminder was queued, false if this booking was already reminded */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean eventReminder(Booking booking) {
        BookingEmailData data = bookingData(booking, true);
        return outbox.enqueue(EmailType.EVENT_REMINDER, data.recipientEmail(),
                templates.eventReminder(data), "REMINDER:booking:" + booking.getId());
    }

    // ---------- helpers ----------

    private boolean wasRefunded(Booking booking) {
        return paymentRepository.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.REFUNDED);
    }

    private BookingEmailData bookingData(Booking booking, boolean includeCodes) {
        User user = booking.getUser();
        TicketType ticketType = booking.getTicketType();
        Event event = ticketType.getEvent();

        List<String> codes = includeCodes
                ? ticketRepository.findByBookingIdIn(List.of(booking.getId()))
                .stream().map(Ticket::getTicketCode).toList()
                : List.of();

        long minutesToPay = 0;
        if (booking.getExpiresAt() != null) {
            long seconds = Duration.between(LocalDateTime.now(), booking.getExpiresAt()).getSeconds();
            minutesToPay = Math.max(1L, (seconds + 59) / 60);
        }

        return new BookingEmailData(booking.getId(), user.getName(), user.getEmail(),
                event.getId(), event.getTitle(), event.getStartTime(), event.getVenue(), event.getCity(),
                ticketType.getName(), booking.getQuantity(), booking.getTotalAmount(),
                codes, minutesToPay);
    }

    private WaitlistEmailData waitlistData(WaitingList entry, Long position) {
        User user = entry.getUser();
        TicketType ticketType = entry.getTicketType();
        Event event = ticketType.getEvent();
        return new WaitlistEmailData(entry.getId(), user.getName(), user.getEmail(),
                event.getId(), event.getTitle(), event.getStartTime(),
                ticketType.getName(), entry.getQuantity(), position);
    }
}