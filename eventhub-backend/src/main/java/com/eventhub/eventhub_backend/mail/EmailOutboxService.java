package com.eventhub.eventhub_backend.mail;

import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.enums.EmailType;
import com.eventhub.eventhub_backend.repository.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailMessageRepository repository;

    /**
     * Queues an email as part of the caller's transaction: if the booking rolls back,
     * the email disappears with it.
     *
     * @return false if an email with this dedupe key already exists (nothing was queued)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(EmailType type, String to, EmailContent content, String dedupeKey) {
        if (dedupeKey != null && repository.existsByDedupeKey(dedupeKey)) {
            return false;
        }

        EmailMessage message = new EmailMessage();
        message.setType(type);
        message.setRecipient(to);
        // The column holds 255 characters. A long event title must never break a booking.
        message.setSubject(shorten(content.subject(), 250));
        message.setTextBody(content.text());
        message.setHtmlBody(content.html());
        message.setDedupeKey(dedupeKey);
        message.setNextAttemptAt(LocalDateTime.now());
        repository.save(message);
        return true;
    }

    private String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}