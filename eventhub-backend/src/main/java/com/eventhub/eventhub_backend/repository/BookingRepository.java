package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.ticketType tt
            join fetch tt.event
            where b.user.id = :userId
            order by b.createdAt desc, b.id desc
            """)
    List<Booking> findAllByUserWithDetails(@Param("userId") Long userId);

    @Query("""
            select b from Booking b
            join fetch b.ticketType tt
            join fetch tt.event
            where b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    // Locks this one booking row, so cancel, pay and expire take turns
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    // Unpaid bookings whose payment window has closed
    @Query("select b.id from Booking b where b.status = :status and b.expiresAt < :now")
    List<Long> findOverdueIds(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    long countByTicketTypeId(Long ticketTypeId);

    long countByTicketTypeIdAndStatus(Long ticketTypeId, BookingStatus status);


    // Ids only: we then lock and re-read each booking one by one
    @Query("""
            select b.id from Booking b
            where b.ticketType.event.id = :eventId and b.status in :statuses
            order by b.id
            """)
    List<Long> findIdsByEventAndStatusIn(@Param("eventId") Long eventId,
                                         @Param("statuses") Collection<BookingStatus> statuses);
}