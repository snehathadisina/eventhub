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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final TicketTypeRepository ticketTypeRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final BookingCreator bookingCreator;
    private final WaitingListPromotionService waitingListPromotionService;

    @Value("${app.booking.stock-strategy:lock}")
    private String stockStrategy;

    private boolean useAtomicUpdate;

    @PostConstruct
    void configureStockStrategy() {
        if ("atomic".equalsIgnoreCase(stockStrategy)) {
            useAtomicUpdate = true;
        } else if ("lock".equalsIgnoreCase(stockStrategy)) {
            useAtomicUpdate = false;
        } else {
            throw new IllegalStateException(
                    "app.booking.stock-strategy must be 'lock' or 'atomic' but was '" + stockStrategy + "'");
        }
    }

    @Transactional
    public BookingResponse book(BookingRequest request, Long userId) {
        TicketType ticketType = loadTicketType(request.ticketTypeId());

        Event event = ticketType.getEvent();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            // Same message as "not found", so hidden or cancelled events aren't revealed
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");
        }
        if (!event.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }

        int quantity = request.quantity();
        reserveStock(ticketType, quantity);

        return bookingCreator.create(userId, ticketType, quantity);
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

    @Transactional
    public BookingResponse cancel(Long bookingId, Long userId) {
        // Someone else's booking gets the same 404 as a missing one, so it isn't revealed
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only confirmed bookings can be cancelled");
        }

        TicketType ticketType = booking.getTicketType();
        Event event = ticketType.getEvent();
        if (!event.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }
        if (ticketRepository.countCheckedInByBooking(booking.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Some tickets from this booking were already used");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        int restored = ticketTypeRepository.incrementStock(ticketType.getId(), booking.getQuantity());
        if (restored == 0) {
            // Should never happen. Throwing rolls back the whole cancellation.
            throw new IllegalStateException("Could not restore stock for booking " + bookingId);
        }

        // Day 13: the freed tickets go to the people waiting, in the same transaction
        waitingListPromotionService.promoteFor(ticketType.getId());

        List<Ticket> tickets = ticketRepository.findByBookingIdIn(List.of(booking.getId()));
        return BookingResponse.from(booking, tickets);
    }

    private TicketType loadTicketType(Long ticketTypeId) {
        Optional<TicketType> found = useAtomicUpdate
                ? ticketTypeRepository.findById(ticketTypeId)
                : ticketTypeRepository.findByIdForUpdate(ticketTypeId);

        return found.orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found"));
    }

    private void reserveStock(TicketType ticketType, int quantity) {
        if (useAtomicUpdate) {
            // One statement checks and subtracts together. No other buyer can slip in between.
            int updated = ticketTypeRepository.decrementStock(ticketType.getId(), quantity);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough tickets available");
            }
        } else {
            // The row is locked by findByIdForUpdate, so this read-check-write is safe.
            if (ticketType.getAvailableQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough tickets available");
            }
            ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() - quantity);
        }
    }
}