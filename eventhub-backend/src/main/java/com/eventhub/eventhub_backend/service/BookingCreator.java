package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingCreator {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Value("${app.payment.enabled:true}")
    private boolean paymentEnabled;

    @Value("${app.payment.hold-minutes:10}")
    private long holdMinutes;

    /**
     * Saves a booking and one ticket per seat. The caller must already have reserved the stock
     * and must be inside a transaction.
     * Paid tickets start PENDING and hold the stock until the payment window closes.
     * Free tickets (or payment switched off) are CONFIRMED straight away.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BookingResponse create(Long userId, TicketType ticketType, int quantity) {
        BigDecimal total = ticketType.getPrice().multiply(BigDecimal.valueOf(quantity));
        boolean needsPayment = paymentEnabled && total.signum() > 0;

        Booking booking = new Booking();
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setTicketType(ticketType);
        booking.setQuantity(quantity);
        booking.setTotalAmount(total);
        booking.setStatus(needsPayment ? BookingStatus.PENDING : BookingStatus.CONFIRMED);
        booking.setExpiresAt(needsPayment ? LocalDateTime.now().plusMinutes(holdMinutes) : null);
        bookingRepository.save(booking);

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setTicketCode(UUID.randomUUID().toString());
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);

        return BookingResponse.from(booking, tickets);
    }
}