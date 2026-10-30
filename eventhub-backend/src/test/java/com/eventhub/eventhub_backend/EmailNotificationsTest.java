package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.EmailMessage;
import com.eventhub.eventhub_backend.entity.Payment;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.EmailType;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.PaymentStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.mail.MailTransport;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EmailMessageRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.PaymentRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingExpiryJob;
import com.eventhub.eventhub_backend.service.BookingService;
import com.eventhub.eventhub_backend.service.EventService;
import com.eventhub.eventhub_backend.service.PaymentService;
import com.eventhub.eventhub_backend.service.WaitingListService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.payment.enabled=true", "app.payment.hold-minutes=10"})
class EmailNotificationsTest {

    @MockitoBean private MailTransport transport;

    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private WaitingListService waitingListService;
    @Autowired private EventService eventService;
    @Autowired private BookingExpiryJob bookingExpiryJob;
    @Autowired private EmailMessageRepository emailRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
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
    void payingSendsTheConfirmation_butJustBookingDoesNot() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 2);
        assertThat(typesOf(buyer)).isEmpty();

        paymentService.pay(booking.id(), buyer, "4242");

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED);
        assertThat(textOf(buyer, EmailType.BOOKING_CONFIRMED)).contains(booking.ticketCodes().get(0));
    }

    @Test
    void cancellingAPaidBooking_mentionsTheRefund() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 2);
        paymentService.pay(booking.id(), buyer, "4242");

        bookingService.cancel(booking.id(), buyer);

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED, EmailType.BOOKING_CANCELLED);
        assertThat(textOf(buyer, EmailType.BOOKING_CANCELLED)).containsIgnoringCase("refund of");
    }

    @Test
    void cancellingAnUnpaidBooking_hasNoRefundLine() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);

        bookingService.cancel(booking.id(), buyer);

        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CANCELLED);
        assertThat(textOf(buyer, EmailType.BOOKING_CANCELLED)).doesNotContainIgnoringCase("refund");
    }

    @Test
    void joiningTheWaitingList_tellsYouYourPosition() {
        Long ticketTypeId = data.ticketType(1);
        book(ticketTypeId, data.buyer(), 1);
        Long waiter = data.buyer();

        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), waiter);

        assertThat(typesOf(waiter)).containsExactly(EmailType.WAITLIST_JOINED);
        assertThat(textOf(waiter, EmailType.WAITLIST_JOINED)).contains("number 1");
    }

    @Test
    void whenAHoldExpires_theHolderIsToldAndTheNextPersonIsAskedToPay() {
        Long ticketTypeId = data.ticketType(2);
        Long holder = data.buyer();
        Long waiter = data.buyer();
        BookingResponse held = book(ticketTypeId, holder, 2); // unpaid
        waitingListService.join(new WaitingListRequest(ticketTypeId, 2), waiter);

        expireNow(held.id());
        bookingExpiryJob.run();

        assertThat(typesOf(holder)).containsExactly(EmailType.BOOKING_EXPIRED);
        assertThat(typesOf(waiter)).containsExactly(EmailType.WAITLIST_JOINED, EmailType.TICKETS_HELD);
        BookingResponse promoted = bookingService.listMine(waiter).get(0);
        assertThat(textOf(waiter, EmailType.TICKETS_HELD)).contains("/checkout/" + promoted.id());
    }

    @Test
    void whenAPaidBookingIsCancelled_theNextPersonWaitingIsAskedToPay() {
        Long ticketTypeId = data.ticketType(1);
        Long owner = data.buyer();
        Long waiter = data.buyer();
        BookingResponse booking = book(ticketTypeId, owner, 1);
        paymentService.pay(booking.id(), owner, "4242");
        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), waiter);

        bookingService.cancel(booking.id(), owner);

        assertThat(typesOf(owner)).containsExactly(EmailType.BOOKING_CONFIRMED, EmailType.BOOKING_CANCELLED);
        assertThat(typesOf(waiter)).containsExactly(EmailType.WAITLIST_JOINED, EmailType.TICKETS_HELD);
    }

    @Test
    void cancellingAnEvent_refundsPaidBookings_andNotifiesEveryone() {
        Long ticketTypeId = data.ticketType(2);
        Long eventId = data.eventId(ticketTypeId);
        Long organizer = data.organizerId(ticketTypeId);
        Long payer = data.buyer();
        Long unpaid = data.buyer();
        Long waiting = data.buyer();

        BookingResponse paid = book(ticketTypeId, payer, 1);
        paymentService.pay(paid.id(), payer, "4242");
        BookingResponse held = book(ticketTypeId, unpaid, 1);
        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), waiting);

        eventService.cancel(eventId, organizer);

        assertThat(eventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(statusOf(paid.id())).isEqualTo(BookingStatus.CANCELLED);
        assertThat(paymentStatuses(paid.id())).containsExactly(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        assertThat(statusOf(held.id())).isEqualTo(BookingStatus.CANCELLED);
        assertThat(paymentStatuses(held.id())).isEmpty();
        assertThat(waitingListService.listMine(waiting).get(0).status()).isEqualTo(WaitingListStatus.CANCELLED);

        assertThat(typesOf(payer)).contains(EmailType.EVENT_CANCELLED);
        assertThat(textOf(payer, EmailType.EVENT_CANCELLED)).containsIgnoringCase("refund of");
        assertThat(typesOf(unpaid)).contains(EmailType.EVENT_CANCELLED);
        assertThat(textOf(unpaid, EmailType.EVENT_CANCELLED)).doesNotContainIgnoringCase("refund");
        assertThat(typesOf(waiting)).contains(EmailType.EVENT_CANCELLED);

        assertRejected(409, "cancelled", () -> paymentService.pay(held.id(), unpaid, "4242"));
    }

    @Test
    void cancellingAnEventTwice_doesNothingTheSecondTime() {
        Long ticketTypeId = data.ticketType(2);
        Long eventId = data.eventId(ticketTypeId);
        Long organizer = data.organizerId(ticketTypeId);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);
        paymentService.pay(booking.id(), buyer, "4242");

        eventService.cancel(eventId, organizer);
        eventService.cancel(eventId, organizer);

        assertThat(paymentStatuses(booking.id())).containsExactly(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
//        assertThat(typesOf(buyer).stream().filter(t -> t == EventType()).count()).isEqualTo(1L);
        assertThat(typesOf(buyer)).containsExactly(EmailType.BOOKING_CONFIRMED, EmailType.EVENT_CANCELLED);
    }

    private EmailType EventType() {
        return EmailType.EVENT_CANCELLED;
    }

    private BookingResponse book(Long ticketTypeId, Long buyer, int quantity) {
        return bookingService.book(new BookingRequest(ticketTypeId, quantity), buyer);
    }

    private List<EmailMessage> mailsOf(Long userId) {
        String email = userRepository.findById(userId).orElseThrow().getEmail();
        return emailRepository.findByRecipientOrderByIdAsc(email);
    }

    private List<EmailType> typesOf(Long userId) {
        return mailsOf(userId).stream().map(EmailMessage::getType).toList();
    }

    private String textOf(Long userId, EmailType type) {
        return mailsOf(userId).stream()
                .filter(mail -> mail.getType() == type)
                .findFirst().orElseThrow()
                .getTextBody();
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    private List<PaymentStatus> paymentStatuses(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
                .map(Payment::getStatus).toList();
    }

    private void expireNow(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);
    }

    private void assertRejected(int status, String messagePart, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(status);
            assertThat(e.getReason()).containsIgnoringCase(messagePart);
        });
    }
}