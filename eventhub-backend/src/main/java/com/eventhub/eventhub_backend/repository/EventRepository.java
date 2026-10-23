package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    List<Event> findByOrganizerIdOrderByStartTimeDesc(Long organizerId);
}