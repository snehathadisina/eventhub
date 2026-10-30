
package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.WaitingListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingListPromotionService {

    private final WaitingListRepository waitingListRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final BookingCreator bookingCreator;

    /**
     * Hands freed tickets to the people waiting, oldest first.
     * Runs inside the caller's transaction (the cancellation), so nobody else can
     * buy the freed tickets in between.
     *
     * @return how many waiting-list entries were promoted
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int promoteFor(Long ticketTypeId) {
        List<WaitingList> queue =
                waitingListRepository.findQueueForUpdate(ticketTypeId, WaitingListStatus.WAITING);
        if (queue.isEmpty()) {
            return 0;
        }

        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        if (ticketType.getEvent().getStatus() != EventStatus.PUBLISHED) {
            return 0; // never promote people into a cancelled or hidden event
        }

        int promoted = 0;
        for (WaitingList entry : queue) {
            int quantity = entry.getQuantity();

            // Same atomic statement as normal booking: returns 1 only if enough tickets remain.
            if (ticketTypeRepository.decrementStock(ticketTypeId, quantity) == 1) {
//                bookingCreator.create(entry.getUser().getId(), ticketType, quantity);
                bookingCreator.create(entry.getUser().getId(), ticketType, quantity, true);
                entry.setStatus(WaitingListStatus.PROMOTED);
                promoted++;
            }
            // Otherwise this request doesn't fit yet: it keeps its place, and we try the next one.
        }

        if (promoted > 0) {
            log.info("Promoted {} waiting-list entries for ticket type {}", promoted, ticketTypeId);
        }
        return promoted;
    }
}