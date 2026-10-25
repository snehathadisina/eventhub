package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBookingIdIn(Collection<Long> bookingIds);
}