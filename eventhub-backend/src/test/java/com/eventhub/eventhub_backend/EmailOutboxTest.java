package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EmailStatus;
import com.eventhub.eventhub_backend.enums.EmailType;
import com.eventhub.eventhub_backend.mail.EmailSenderJob;
import com.eventhub.eventhub_backend.mail.MailTransport;
import com.eventhub.eventhub_backend.mail.NotificationService;
import com.eventhub.eventhub_backend.repository.BookingRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.mail.max-attempts=2")
class EmailOutboxTest {

    @MockitoBean private MailTransport transport;

    @Autowired private BookingService bookingService;
    @Autowired private EmailSenderJob emailSenderJob;
    @Autowired private NotificationService notificationService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EmailMessageRepository emailRepository;
    @Autowired private BookingRepository bookingRepository;
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
    void aConfirmedBooking_queuesAnEmail_andSendsNothingYet() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();

        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), buyer);

        List<EmailMessage> mails = emailRepository.findByRecipientOrderByIdAsc(emailOf(buyer));
        assertThat(mails).hasSize(1);
        EmailMessage mail = mails.get(0);
        assertThat(mail.getType()).isEqualTo(EmailType.BOOKING_CONFIRMED);
        assertThat(mail.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(mail.getSubject()).contains("Test Event");
        assertThat(mail.getTextBody())
                .contains(booking.ticketCodes().get(0))
                .contains(booking.ticketCodes().get(1))
                .contains("/my-tickets");
        assertThat(mail.getHtmlBody()).contains("Hi Buyer");
        verifyNoInteractions(transport); // booking never waits for the mail server
    }

    @Test
    void theSenderJobDeliversQueuedEmails() throws Exception {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        emailSenderJob.run();

        verify(transport).send(eq(emailOf(buyer)), contains("Test Event"), anyString(), anyString());
        EmailMessage mail = emailRepository.findByRecipientOrderByIdAsc(emailOf(buyer)).get(0);
        assertThat(mail.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(mail.getSentAt()).isNotNull();
        assertThat(mail.getAttempts()).isEqualTo(1);
    }

    @Test
    void aFailedSendIsRetriedLater_thenGivenUp() throws Exception {
        doThrow(new RuntimeException("smtp down")).when(transport).send(any(), any(), any(), any());
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        emailSenderJob.run();

        EmailMessage afterFirst = onlyMailOf(buyer);
        assertThat(afterFirst.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).contains("smtp down");
        assertThat(afterFirst.getNextAttemptAt()).isAfter(LocalDateTime.now());

        makeDue(afterFirst);
        emailSenderJob.run();

        EmailMessage afterSecond = onlyMailOf(buyer);
        assertThat(afterSecond.getStatus()).isEqualTo(EmailStatus.FAILED); // max attempts is 2 in this test
        assertThat(afterSecond.getAttempts()).isEqualTo(2);
    }

    @Test
    void anEmailThatFailsOnceIsSentOnTheRetry() throws Exception {
        doThrow(new RuntimeException("smtp down")).doNothing().when(transport).send(any(), any(), any(), any());
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        emailSenderJob.run();
        makeDue(onlyMailOf(buyer));
        emailSenderJob.run();

        EmailMessage mail = onlyMailOf(buyer);
        assertThat(mail.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(mail.getAttempts()).isEqualTo(2);
        assertThat(mail.getLastError()).isNull();
    }

    @Test
    void theSameEmailIsNeverQueuedTwice() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        transactionTemplate.executeWithoutResult(status -> {
            Booking loaded = bookingRepository.findByIdWithDetails(booking.id()).orElseThrow();
            notificationService.bookingConfirmed(loaded, false); // same booking again
        });

        assertThat(emailRepository.findByRecipientOrderByIdAsc(emailOf(buyer))).hasSize(1);
    }

    @Test
    void userTextIsEscaped_andSubjectsStayOnOneLine() {
        Long ticketTypeId = data.ticketType(5);
        Event event = eventRepository.findById(data.eventId(ticketTypeId)).orElseThrow();
        event.setTitle("Party\r\nBcc: evil@example.com <script>alert(1)</script>");
        eventRepository.save(event);
        Long buyer = data.buyer();

        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        EmailMessage mail = onlyMailOf(buyer);
        assertThat(mail.getSubject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(mail.getHtmlBody()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void aVeryLongTitle_doesNotBreakTheBooking() {
        Long ticketTypeId = data.ticketType(5);
        Event event = eventRepository.findById(data.eventId(ticketTypeId)).orElseThrow();
        event.setTitle("x".repeat(250));
        eventRepository.save(event);
        Long buyer = data.buyer();

        bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);

        assertThat(onlyMailOf(buyer).getSubject().length()).isLessThanOrEqualTo(250);
    }

    private String emailOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getEmail();
    }

    private EmailMessage onlyMailOf(Long userId) {
        List<EmailMessage> mails = emailRepository.findByRecipientOrderByIdAsc(emailOf(userId));
        assertThat(mails).hasSize(1);
        return mails.get(0);
    }

    private void makeDue(EmailMessage mail) {
        mail.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        emailRepository.save(mail);
    }
}