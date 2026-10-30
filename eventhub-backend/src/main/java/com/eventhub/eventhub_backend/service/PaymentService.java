package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.dto.PaymentResponse;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Payment;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.PaymentStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.PaymentRepository;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.eventhub.eventhub_backend.mail.NotificationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway gateway;
    private final NotificationService notificationService;

    @Transactional
    public PaymentResponse pay(Long bookingId, Long userId, String cardLast4) {
        // Locking the booking row means a double-click, a cancel and the expiry job take turns
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already paid");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking was cancelled, so it can no longer be paid");
        }
        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The payment window has expired and the tickets were released");
        }

        MockPaymentGateway.Result result = gateway.charge(cardLast4, booking.getTotalAmount());

        // Every attempt is recorded, successful or not
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setCardLast4(cardLast4);
        payment.setStatus(result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setReference(result.reference());
        payment.setFailureReason(result.success() ? null : result.message());
        paymentRepository.save(payment);

        if (result.success()) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setExpiresAt(null);
            notificationService.bookingConfirmed(booking, false);
        }

        List<Ticket> tickets = ticketRepository.findByBookingIdIn(List.of(booking.getId()));
        return new PaymentResponse(result.success(), result.message(), result.reference(),
                BookingResponse.from(booking, tickets));
    }

    /** Records a refund if this booking was paid. Must run inside the cancellation's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundIfPaid(Booking booking) {
        Optional<Payment> paid =
                paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), PaymentStatus.SUCCESS);
        if (paid.isEmpty()
                || paymentRepository.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.REFUNDED)) {
            return;
        }

        Payment refund = new Payment();
        refund.setBooking(booking);
        refund.setAmount(paid.get().getAmount());
        refund.setCardLast4(paid.get().getCardLast4());
        refund.setStatus(PaymentStatus.REFUNDED);
        refund.setReference(gateway.newReference("REFUND"));
        paymentRepository.save(refund);
    }
}