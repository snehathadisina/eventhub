package com.eventhub.eventhub_backend.entity;

import com.eventhub.eventhub_backend.enums.EmailStatus;
import com.eventhub.eventhub_backend.enums.EmailType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailType type;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String textBody;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    // One email per real-world event, e.g. "CONFIRMED:booking:42". Stops duplicates.
    @Column(name = "dedupe_key", length = 120, unique = true)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status = EmailStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    // The sender only picks up emails whose time has come. Also used as a short "lease" while sending.
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}