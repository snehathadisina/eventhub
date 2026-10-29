package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Payment;
import com.eventhub.eventhub_backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingIdOrderByIdAsc(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}