package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingRepository bookingRepository;
    private final BookingExpiryService bookingExpiryService;

    @Scheduled(fixedDelayString = "${app.payment.expiry-check-seconds:30}",
            initialDelayString = "${app.payment.expiry-check-seconds:30}",
            timeUnit = TimeUnit.SECONDS)
    public int run() {
        List<Long> overdue = bookingRepository.findOverdueIds(BookingStatus.PENDING, LocalDateTime.now());

        int released = 0;
        for (Long bookingId : overdue) {
            try {
                if (bookingExpiryService.expireIfOverdue(bookingId)) {
                    released++;
                }
            } catch (RuntimeException e) {
                // One bad booking must not stop the others
                log.warn("Could not release booking {}: {}", bookingId, e.getMessage());
            }
        }

        if (released > 0) {
            log.info("Released {} unpaid bookings", released);
        }
        return released;
    }
}