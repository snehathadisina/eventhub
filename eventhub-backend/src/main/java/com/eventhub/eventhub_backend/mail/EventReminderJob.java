package com.eventhub.eventhub_backend.mail;

import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
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
public class EventReminderJob {

    private final BookingRepository bookingRepository;
    private final EventReminderService eventReminderService;

    @Value("${app.reminders.hours-before:24}")
    private int hoursBefore;

    @Scheduled(fixedDelayString = "${app.reminders.check-minutes:60}",
            initialDelayString = "${app.reminders.check-minutes:60}",
            timeUnit = TimeUnit.MINUTES)
    public int run() {
        return sendDueReminders(LocalDateTime.now());
    }

    public int sendDueReminders(LocalDateTime now) {
        List<Long> candidates = bookingRepository.findReminderCandidateIds(
                BookingStatus.CONFIRMED, EventStatus.PUBLISHED, now, now.plusHours(hoursBefore));

        int queued = 0;
        for (Long bookingId : candidates) {
            try {
                if (eventReminderService.remindIfDue(bookingId, now, hoursBefore)) {
                    queued++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not queue a reminder for booking {}: {}", bookingId, e.getMessage());
            }
        }

        if (queued > 0) {
            log.info("Queued {} event reminders", queued);
        }
        return queued;
    }
}