package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.WaitingList;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitingListRepository extends JpaRepository<WaitingList, Long> {

    boolean existsByUserIdAndTicketTypeIdAndStatus(Long userId, Long ticketTypeId,
                                                   WaitingListStatus status);

    long countByTicketTypeIdAndStatus(Long ticketTypeId, WaitingListStatus status);

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

    // The whole queue for one ticket type, oldest first, locked while we promote.
    // A locking read always sees the latest committed data, never a stale snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from WaitingList w
            where w.ticketType.id = :ticketTypeId and w.status = :status
            order by w.id asc
            """)
    List<WaitingList> findQueueForUpdate(@Param("ticketTypeId") Long ticketTypeId,
                                         @Param("status") WaitingListStatus status);

    // One entry, locked, so "leave" and "promote" can't overwrite each other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitingList w where w.id = :id")
    Optional<WaitingList> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from WaitingList w
            where w.ticketType.event.id = :eventId and w.status = :status
            order by w.id asc
            """)
    List<WaitingList> findWaitingForEventForUpdate(@Param("eventId") Long eventId,
                                                   @Param("status") WaitingListStatus status);
}