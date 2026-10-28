package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitingListRepository extends JpaRepository<WaitingList, Long> {

    boolean existsByUserIdAndTicketTypeIdAndStatus(Long userId, Long ticketTypeId,
                                                   WaitingListStatus status);

    @Query("""
            select w from WaitingList w
            join fetch w.ticketType tt
            join fetch tt.event
            where w.user.id = :userId
            order by w.id desc
            """)
    List<WaitingList> findAllByUserWithDetails(@Param("userId") Long userId);

    @Query("""
            select w from WaitingList w
            join fetch w.ticketType tt
            join fetch tt.event
            where w.id = :id
            """)
    Optional<WaitingList> findByIdWithDetails(@Param("id") Long id);

    // Number of people still waiting who joined before this entry (ids grow in joining order)
    @Query("""
            select count(w) from WaitingList w
            where w.ticketType.id = :ticketTypeId and w.status = :status and w.id < :id
            """)
    long countAhead(@Param("ticketTypeId") Long ticketTypeId,
                    @Param("status") WaitingListStatus status,
                    @Param("id") Long id);
}