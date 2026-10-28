package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingIdIn(Collection<Long> bookingIds);

    @Query("select count(t) from Ticket t where t.booking.ticketType.id = :ticketTypeId")
    long countByTicketTypeId(@Param("ticketTypeId") Long ticketTypeId);
}