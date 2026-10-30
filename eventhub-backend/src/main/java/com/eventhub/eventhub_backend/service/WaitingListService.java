package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.dto.WaitingListResponse;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.mail.NotificationService;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.repository.WaitingListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaitingListService {

    private final WaitingListRepository waitingListRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public WaitingListResponse join(WaitingListRequest request, Long userId) {
        // Locking the ticket type row makes joins, bookings and promotions take turns
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.ticketTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found"));

        Event event = ticketType.getEvent();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");
        }
        if (!event.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }

        int quantity = request.quantity();
        if (quantity > ticketType.getTotalQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You asked for more tickets than this ticket type has in total");
        }
        if (ticketType.getAvailableQuantity() >= quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tickets are available. Please book them directly");
        }
        if (waitingListRepository.existsByUserIdAndTicketTypeIdAndStatus(
                userId, ticketType.getId(), WaitingListStatus.WAITING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are already on the waiting list for this ticket type");
        }

        WaitingList entry = new WaitingList();
        entry.setUser(userRepository.getReferenceById(userId));
        entry.setTicketType(ticketType);
        entry.setQuantity(quantity);
        waitingListRepository.save(entry);

//        return WaitingListResponse.from(entry, positionOf(entry));
        Long position = positionOf(entry);
        notificationService.waitlistJoined(entry, position);
        return WaitingListResponse.from(entry, position);
    }

    @Transactional(readOnly = true)
    public List<WaitingListResponse> listMine(Long userId) {
        return waitingListRepository.findAllByUserWithDetails(userId).stream()
                .map(w -> WaitingListResponse.from(w, positionOf(w)))
                .toList();
    }

    @Transactional
    public WaitingListResponse leave(Long entryId, Long userId) {
        // Locking read: if a promotion is working on this entry we wait, then see its final status
        WaitingList entry = waitingListRepository.findByIdForUpdate(entryId)
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Waiting list entry not found"));

        if (entry.getStatus() == WaitingListStatus.PROMOTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already got these tickets. Cancel the booking instead");
        }
        if (entry.getStatus() != WaitingListStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are no longer waiting for this ticket type");
        }

        entry.setStatus(WaitingListStatus.CANCELLED);
        return WaitingListResponse.from(entry, null);
    }

    private Long positionOf(WaitingList entry) {
        if (entry.getStatus() != WaitingListStatus.WAITING) {
            return null;
        }
        return waitingListRepository.countAhead(
                entry.getTicketType().getId(), WaitingListStatus.WAITING, entry.getId()) + 1;
    }
}