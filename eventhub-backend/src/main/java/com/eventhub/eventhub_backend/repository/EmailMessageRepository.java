package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.enums.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<EmailMessage> findByRecipientOrderByIdAsc(String recipient);

    long countByStatus(EmailStatus status);

    List<EmailMessage> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(EmailStatus status,
                                                                                  LocalDateTime now);

    // Claim = push the time forward so no other runner sends this email while we do.
    // Returns 1 for the one runner that got it.
    @Transactional
    @Modifying
    @Query("""
            update EmailMessage m
            set m.attempts = m.attempts + 1, m.nextAttemptAt = :leaseUntil
            where m.id = :id and m.status = :pending and m.nextAttemptAt <= :now
            """)
    int claim(@Param("id") Long id,
              @Param("pending") EmailStatus pending,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Transactional
    @Modifying
    @Query("update EmailMessage m set m.status = :status, m.sentAt = :now, m.lastError = null where m.id = :id")
    int markSent(@Param("id") Long id, @Param("status") EmailStatus status, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("""
            update EmailMessage m
            set m.status = :status, m.nextAttemptAt = :next, m.lastError = :error
            where m.id = :id
            """)
    int markAttemptFailed(@Param("id") Long id,
                          @Param("status") EmailStatus status,
                          @Param("next") LocalDateTime next,
                          @Param("error") String error);
}