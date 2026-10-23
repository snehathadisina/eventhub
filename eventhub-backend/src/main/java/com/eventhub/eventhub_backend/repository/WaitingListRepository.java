package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.WaitingList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitingListRepository extends JpaRepository<WaitingList, Long> {

}
