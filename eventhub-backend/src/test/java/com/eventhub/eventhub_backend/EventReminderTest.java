package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EmailType;
import com.eventhub.eventhub_backend.mail.EventReminderJob;
import com.eventhub.eventhub_backend.mail.MailTransport;
import com.eventhub.eventhub_backend.repository.EmailMessageRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventReminderTest {

    @MockitoBean private MailTransport transport;

    @Autowired private BookingService bookingService;
    @Autowired private EventReminderJob reminderJob;
    @Autowired private EmailMessageRepository emailRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private TestData data;

    @BeforeEach
    void setUp() {
        assertThat(datasourceUrl)
                .as("Refusing to run: this test must only use the eventhub_test database")
                .contains("eventhub_test");
        data = new TestData(userRepository, eventRepository, ticketTypeRepository);
        emailRepository.deleteAll();
    }

    @Test
    void aBookingMadeEarly_getsExactlyOneReminder() {
        Long ticketTypeId = data.ticketType(5);
        LocalDateTime start = startFarAway(ticketTypeId);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 2), buyer);

        reminderJob.sendDueReminders(start.minusHours(20)); // pretend it is 20 hours before
        reminderJob.sendDueReminders(start.minusHours(19)); // and an hour later

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED, EmailType.EVENT_REMINDER);
    }

    @Test
    void tooEarly_noReminder() {
        Long ticketTypeId = data.ticketType(5);
        LocalDateTime start = startFarAway(ticketTypeId);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        reminderJob.sendDueReminders(start.minusHours(30));

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED);
    }

    @Test
    void afterTheEventStarted_noReminder() {
        Long ticketTypeId = data.ticketType(5);
        LocalDateTime start = startFarAway(ticketTypeId);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        reminderJob.sendDueReminders(start.plusHours(1));

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED);
    }

    @Test
    void aCancelledBooking_noReminder() {
        Long ticketTypeId = data.ticketType(5);
        LocalDateTime start = startFarAway(ticketTypeId);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);
        bookingService.cancel(booking.id(), buyer);

        reminderJob.sendDueReminders(start.minusHours(20));

        assertThat(typesOf(buyer)).doesNotContain(EmailType.EVENT_REMINDER);
    }

    @Test
    void aCancelledEvent_noReminder() {
        Long ticketTypeId = data.ticketType(5);
        LocalDateTime start = startFarAway(ticketTypeId);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);
        data.cancelEvent(ticketTypeId);

        reminderJob.sendDueReminders(start.minusHours(20));

        assertThat(typesOf(buyer)).doesNotContain(EmailType.EVENT_REMINDER);
    }

    @Test
    void aBookingMadeInsideTheWindow_isNotRemindedImmediately() {
        Long ticketTypeId = data.ticketType(5);
        Event event = eventRepository.findById(data.eventId(ticketTypeId)).orElseThrow();
        event.setStartTime(LocalDateTime.now().plusHours(10)); // already inside the 24 hour window
        eventRepository.save(event);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        reminderJob.sendDueReminders(LocalDateTime.now());

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED);
    }

    /** A start time far from every other test's events, so the reminder window only catches ours. */
    private LocalDateTime startFarAway(Long ticketTypeId) {
        Event event = eventRepository.findById(data.eventId(ticketTypeId)).orElseThrow();
        event.setStartTime(LocalDateTime.now().plusDays(400));
        eventRepository.save(event);
        return event.getStartTime();
    }

    private List<EmailType> typesOf(Long userId) {
        String email = userRepository.findById(userId).orElseThrow().getEmail();
        return emailRepository.findByRecipientOrderByIdAsc(email).stream().map(EmailMessage::getType).toList();
    }
}