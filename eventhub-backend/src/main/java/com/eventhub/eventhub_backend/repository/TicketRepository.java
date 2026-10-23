package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

}