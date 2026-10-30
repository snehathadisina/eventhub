package com.eventhub.eventhub_backend.mail;

import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.enums.EmailStatus;
import com.eventhub.eventhub_backend.repository.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSenderJob {

    private static final int MAX_BATCHES_PER_RUN = 25;

    private final EmailMessageRepository repository;
    private final MailTransport transport;

    @Value("${app.mail.max-attempts:5}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.mail.send-interval-seconds:5}",
            initialDelayString = "${app.mail.send-interval-seconds:5}",
            timeUnit = TimeUnit.SECONDS)
    public int run() {
        int sent = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<EmailMessage> due = repository
                    .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(EmailStatus.PENDING, LocalDateTime.now());
            if (due.isEmpty()) {
                break;
            }
            for (EmailMessage message : due) {
                try {
                    if (deliver(message)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    // e.g. the database blinked while recording the result. The lease expires and we retry.
                    log.error("Unexpected problem with email {}: {}", message.getId(), e.getMessage());
                }
            }
        }
        return sent;
    }

    private boolean deliver(EmailMessage message) {
        LocalDateTime now = LocalDateTime.now();

        // Claim it first: nobody else will send this email for the next 2 minutes
        if (repository.claim(message.getId(), EmailStatus.PENDING, now, now.plusMinutes(2)) == 0) {
            return false;
        }
        int attempt = message.getAttempts() + 1;

        try {
            transport.send(message.getRecipient(), message.getSubject(), message.getTextBody(), message.getHtmlBody());
            repository.markSent(message.getId(), EmailStatus.SENT, LocalDateTime.now());
            return true;
        } catch (Exception e) {
            String error = shorten(e.getClass().getSimpleName() + ": " + e.getMessage());
            if (attempt >= maxAttempts) {
                repository.markAttemptFailed(message.getId(), EmailStatus.FAILED, LocalDateTime.now(), error);
                log.error("Giving up on email {} to {} after {} attempts: {}",
                        message.getId(), message.getRecipient(), attempt, error);
            } else {
                long waitSeconds = Math.min(900L, 30L * attempt * attempt); // 30s, 2min, 4.5min, 8min...
                repository.markAttemptFailed(message.getId(), EmailStatus.PENDING,
                        LocalDateTime.now().plusSeconds(waitSeconds), error);
                log.warn("Email {} to {} failed (attempt {}), retrying in {}s: {}",
                        message.getId(), message.getRecipient(), attempt, waitSeconds, error);
            }
            return false;
        }
    }

    private String shorten(String value) {
        return value.length() <= 490 ? value : value.substring(0, 487) + "...";
    }
}