package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.TicketRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final TicketTypeRepository ticketTypeRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingResponse book(BookingRequest request, Long userId) {
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.ticketTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found"));

        Event event = ticketType.getEvent();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            // Same message as "not found", so hidden or cancelled events aren't revealed
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");
        }
        if (!event.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }

        int quantity = request.quantity();
        if (ticketType.getAvailableQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough tickets available");
        }

        // WARNING: read-then-write with no locking. This is NOT safe when two people book
        // at the same time. We prove it on Day 9 and fix it on Days 10-11.
//        ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() - quantity);
//        ticketTypeRepository.saveAndFlush(ticketType);

        // The row is locked by findByIdForUpdate above, so this read-check-write is safe:
        // other buyers wait until this transaction commits, then see the updated stock.
        ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() - quantity);

        Booking booking = new Booking();
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setTicketType(ticketType);
        booking.setQuantity(quantity);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(ticketType.getPrice().multiply(BigDecimal.valueOf(quantity)));
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

    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(Long userId) {
        List<Booking> bookings = bookingRepository.findAllByUserWithDetails(userId);
        if (bookings.isEmpty()) {
            return List.of();
        }

        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, List<Ticket>> ticketsByBooking = ticketRepository.findByBookingIdIn(bookingIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getBooking().getId()));

        return bookings.stream()
                .map(b -> BookingResponse.from(b, ticketsByBooking.getOrDefault(b.getId(), List.of())))
                .toList();
    }
}