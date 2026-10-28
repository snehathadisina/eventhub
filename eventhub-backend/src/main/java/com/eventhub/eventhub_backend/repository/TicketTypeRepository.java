package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByEventId(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") Long id);

    // Check and subtract in ONE statement. Returns 1 if the tickets were reserved,
    // 0 if there were not enough left.
    @Modifying
    @Query("""
            update TicketType t
            set t.availableQuantity = t.availableQuantity - :quantity
            where t.id = :id and t.availableQuantity >= :quantity
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);


    // Puts tickets back. The extra condition guarantees stock can never exceed the total.
    @Modifying
    @Query("""
            update TicketType t
            set t.availableQuantity = t.availableQuantity + :quantity
            where t.id = :id and t.availableQuantity + :quantity <= t.totalQuantity
            """)
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}