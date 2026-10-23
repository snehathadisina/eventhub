package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

}