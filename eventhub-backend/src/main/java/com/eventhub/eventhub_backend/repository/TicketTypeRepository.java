package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

}