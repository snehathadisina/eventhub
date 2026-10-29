package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingIdIn(Collection<Long> bookingIds);

    @Query("select count(t) from Ticket t where t.booking.ticketType.id = :ticketTypeId")
    long countByTicketTypeId(@Param("ticketTypeId") Long ticketTypeId);

    // Everyone holding a ticket for this event, in one query (no N+1)
    @Query("""
            select t from Ticket t
            join fetch t.booking b
            join fetch b.user u
            join fetch b.ticketType tt
            where tt.event.id = :eventId and b.status = :status
            order by u.name asc, t.id asc
            """)
    List<Ticket> findAttendees(@Param("eventId") Long eventId, @Param("status") BookingStatus status);
}