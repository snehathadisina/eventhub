package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.dto.PaymentResponse;
import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.entity.Booking;
import com.eventhub.eventhub_backend.entity.Payment;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.PaymentStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.PaymentRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingExpiryJob;
import com.eventhub.eventhub_backend.service.BookingService;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.payment.enabled=true", "app.payment.hold-minutes=10"})
class PaymentTest {

    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private WaitingListService waitingListService;
    @Autowired private BookingExpiryJob bookingExpiryJob;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private TestData data;

    @BeforeEach
    void setUp() {
        assertThat(datasourceUrl)
                .as("Refusing to run: this test must only use the eventhub_test database")
                .contains("eventhub_test");
        data = new TestData(userRepository, eventRepository, ticketTypeRepository);
    }

    @Test
    void aPaidTicketStartsPending_andHoldsTheStock() {
        Long ticketTypeId = data.ticketType(5);

        BookingResponse booking = book(ticketTypeId, data.buyer(), 2);

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.expiresAt()).isNotNull();
        assertThat(booking.secondsLeft()).isBetween(1L, 600L);
        assertThat(booking.ticketCodes()).hasSize(2);
        assertThat(stock(ticketTypeId)).isEqualTo(3);
    }

    @Test
    void aSuccessfulPayment_confirmsTheBooking() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 2);

        PaymentResponse result = paymentService.pay(booking.id(), buyer, "4242");

        assertThat(result.success()).isTrue();
        assertThat(result.reference()).startsWith("MOCK-");
        assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.booking().expiresAt()).isNull();
        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentStatuses(booking.id())).containsExactly(PaymentStatus.SUCCESS);
        assertThat(stock(ticketTypeId)).isEqualTo(3);
    }

    @Test
    void aDeclinedCard_keepsTheBookingPending_andTheBuyerCanRetry() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);

        PaymentResponse declined = paymentService.pay(booking.id(), buyer, "0002");
        assertThat(declined.success()).isFalse();
        assertThat(declined.message()).containsIgnoringCase("declined");
        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.PENDING);

        PaymentResponse retry = paymentService.pay(booking.id(), buyer, "4242");
        assertThat(retry.success()).isTrue();
        assertThat(paymentStatuses(booking.id()))
                .containsExactly(PaymentStatus.FAILED, PaymentStatus.SUCCESS);
    }

    @Test
    void payingTwice_isRejected() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);
        paymentService.pay(booking.id(), buyer, "4242");

        assertRejected(409, "already paid", () -> paymentService.pay(booking.id(), buyer, "4242"));
        assertThat(paymentStatuses(booking.id())).containsExactly(PaymentStatus.SUCCESS);
    }

    @Test
    void someoneElseCannotPayMyBooking() {
        Long ticketTypeId = data.ticketType(5);
        BookingResponse booking = book(ticketTypeId, data.buyer(), 1);
        Long stranger = data.buyer();

        assertRejected(404, "not found", () -> paymentService.pay(booking.id(), stranger, "4242"));
    }

    @Test
    void afterTheWindowCloses_paymentIsRejected() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);
        expireNow(booking.id());

        assertRejected(409, "expired", () -> paymentService.pay(booking.id(), buyer, "4242"));
    }

    @Test
    void expiry_releasesTheStock_andOffersItToTheNextPersonWaiting() {
        Long ticketTypeId = data.ticketType(2);
        Long holder = data.buyer();
        Long first = data.buyer();
        Long second = data.buyer();

        BookingResponse held = book(ticketTypeId, holder, 2); // all tickets held, unpaid
        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), first);
        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), second);

        expireNow(held.id());
        assertThat(bookingExpiryJob.run()).isGreaterThanOrEqualTo(1);

        assertThat(statusOf(held.id())).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stock(ticketTypeId)).isZero();

        BookingResponse firstBooking = bookingService.listMine(first).get(0);
        assertThat(firstBooking.status()).isEqualTo(BookingStatus.PENDING); // they must pay too
        assertThat(bookingService.listMine(second).get(0).status()).isEqualTo(BookingStatus.PENDING);
        assertThat(waitingListService.listMine(first).get(0).status()).isEqualTo(WaitingListStatus.PROMOTED);

        assertThat(paymentService.pay(firstBooking.id(), first, "4242").success()).isTrue();
    }

    @Test
    void freeTickets_areConfirmedWithoutPayment() {
        Long ticketTypeId = data.ticketType(5);
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        ticketType.setPrice(BigDecimal.ZERO);
        ticketTypeRepository.save(ticketType);

        BookingResponse booking = book(ticketTypeId, data.buyer(), 2);

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.expiresAt()).isNull();
        assertThat(booking.secondsLeft()).isNull();
    }

    @Test
    void cancellingAPendingBooking_releasesTheStock() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 2);

        bookingService.cancel(booking.id(), buyer);

        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stock(ticketTypeId)).isEqualTo(5);
        assertThat(paymentStatuses(booking.id())).isEmpty();
    }

    @Test
    void cancellingAPaidBooking_recordsARefund() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 2);
        paymentService.pay(booking.id(), buyer, "4242");

        bookingService.cancel(booking.id(), buyer);

        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stock(ticketTypeId)).isEqualTo(5);
        assertThat(paymentStatuses(booking.id()))
                .containsExactly(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
    }

    @Test
    void aDoubleClickOnPay_chargesOnlyOnce() throws Exception {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = book(ticketTypeId, buyer, 1);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger charged = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Queue<String> otherErrors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    if (paymentService.pay(booking.id(), buyer, "4242").success()) {
                        charged.incrementAndGet();
                    }
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) {
                        rejected.incrementAndGet();
                    } else {
                        otherErrors.add(String.valueOf(e.getMessage()));
                    }
                } catch (RuntimeException e) {
                    otherErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertSoftly(softly -> {
            softly.assertThat(otherErrors).as("unexpected errors").isEmpty();
            softly.assertThat(charged.get()).as("successful charges").isEqualTo(1);
            softly.assertThat(rejected.get()).as("rejected as already paid").isEqualTo(threads - 1);
            softly.assertThat(paymentStatuses(booking.id())).as("payment rows")
                    .containsExactly(PaymentStatus.SUCCESS);
        });
    }

    private BookingResponse book(Long ticketTypeId, Long buyer, int quantity) {
        return bookingService.book(new BookingRequest(ticketTypeId, quantity), buyer);
    }

    private int stock(Long ticketTypeId) {
        return ticketTypeRepository.findById(ticketTypeId).orElseThrow().getAvailableQuantity();
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    private List<PaymentStatus> paymentStatuses(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
                .map(Payment::getStatus)
                .toList();
    }

    /** Pretends the payment window closed a minute ago. */
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