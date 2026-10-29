package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Ticket;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingIdIn(Collection<Long> bookingIds);

    @Query("select count(t) from Ticket t where t.booking.ticketType.id = :ticketTypeId")
    long countByTicketTypeId(@Param("ticketTypeId") Long ticketTypeId);

    @Query("""
            select t from Ticket t
            join fetch t.booking b
            join fetch b.user u
            join fetch b.ticketType tt
            where tt.event.id = :eventId and b.status = :status
            order by u.name asc, t.id asc
            """)
    List<Ticket> findAttendees(@Param("eventId") Long eventId, @Param("status") BookingStatus status);

    // The ticket with everything needed to explain a scan result, in one query
    @Query("""
            select t from Ticket t
            join fetch t.booking b
            join fetch b.user u
            join fetch b.ticketType tt
            join fetch tt.event e
            where t.ticketCode = :code
            """)
    Optional<Ticket> findByCodeWithDetails(@Param("code") String code);

    // Marks the ticket used ONLY if it is still unused and its booking is still confirmed.
    // Returns 1 for the one scanner that wins, 0 for everyone else.
    @Modifying
    @Query("""
            update Ticket t
            set t.checkedIn = true, t.checkedInAt = :now
            where t.id = :id and t.checkedIn = false
              and exists (select b.id from Booking b
                          where b.id = t.booking.id and b.status = :status)
            """)
    int markCheckedIn(@Param("id") Long id,
                      @Param("now") LocalDateTime now,
                      @Param("status") BookingStatus status);

    @Query("""
            select count(t) from Ticket t
            where t.booking.ticketType.event.id = :eventId and t.booking.status = :status
            """)
    long countForEvent(@Param("eventId") Long eventId, @Param("status") BookingStatus status);

    @Query("""
            select count(t) from Ticket t
            where t.booking.ticketType.event.id = :eventId and t.booking.status = :status
              and t.checkedIn = true
            """)
    long countCheckedInForEvent(@Param("eventId") Long eventId, @Param("status") BookingStatus status);

    @Query("select count(t) from Ticket t where t.booking.id = :bookingId and t.checkedIn = true")
    long countCheckedInByBooking(@Param("bookingId") Long bookingId);
}