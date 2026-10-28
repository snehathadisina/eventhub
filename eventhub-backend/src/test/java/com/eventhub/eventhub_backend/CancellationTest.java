package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

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
class CancellationTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private BookingRepository bookingRepository;

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
    void cancelling_returnsTicketsToStock() {
        Long ticketTypeId = data.ticketType(10);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 3), buyer);
        assertThat(stock(ticketTypeId)).isEqualTo(7);

        BookingResponse cancelled = bookingService.cancel(booking.id(), buyer);

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stock(ticketTypeId)).isEqualTo(10);
    }

    @Test
    void cancellingTwice_isRejectedAndDoesNotRestockTwice() {
        Long ticketTypeId = data.ticketType(10);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), buyer);
        bookingService.cancel(booking.id(), buyer);

        assertRejected(409, () -> bookingService.cancel(booking.id(), buyer));
        assertThat(stock(ticketTypeId)).isEqualTo(10);
    }

    @Test
    void someoneElseCannotCancelMyBooking() {
        Long ticketTypeId = data.ticketType(10);
        Long owner = data.buyer();
        Long stranger = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), owner);

        assertRejected(404, () -> bookingService.cancel(booking.id(), stranger));

        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(stock(ticketTypeId)).isEqualTo(8);
    }

    @Test
    void cannotCancelAfterTheEventHasStarted() {
        Long ticketTypeId = data.ticketType(10);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), buyer);
        data.startEventInThePast(ticketTypeId);

        assertRejected(409, () -> bookingService.cancel(booking.id(), buyer));
        assertThat(stock(ticketTypeId)).isEqualTo(8);
    }

    @Test
    void twentyThreadsCancelTheSameBooking_onlyOneWins() throws Exception {
        Long ticketTypeId = data.ticketType(10);
        Long buyer = data.buyer();
        Long bookingId = bookingService.book(new BookingRequest(ticketTypeId, 5), buyer).id();
        assertThat(stock(ticketTypeId)).isEqualTo(5);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Queue<String> otherErrors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.cancel(bookingId, buyer);
                    cancelled.incrementAndGet();
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
            softly.assertThat(cancelled.get()).as("cancellations that worked").isEqualTo(1);
            softly.assertThat(rejected.get()).as("rejected as already cancelled").isEqualTo(threads - 1);
            softly.assertThat(stock(ticketTypeId)).as("stock restored exactly once").isEqualTo(10);
        });
    }

    private int stock(Long ticketTypeId) {
        return ticketTypeRepository.findById(ticketTypeId).orElseThrow().getAvailableQuantity();
    }

    private void assertRejected(int status, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(status));
    }
}