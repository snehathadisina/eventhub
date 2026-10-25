package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.ticketType tt
            join fetch tt.event
            where b.user.id = :userId
            order by b.createdAt desc, b.id desc
            """)
    List<Booking> findAllByUserWithDetails(@Param("userId") Long userId);
}