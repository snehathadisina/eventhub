package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.dto.WaitingListResponse;
import com.eventhub.eventhub_backend.enums.BookingStatus;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.repository.WaitingListRepository;
import com.eventhub.eventhub_backend.service.BookingService;
import com.eventhub.eventhub_backend.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest
@ActiveProfiles("test")
class WaitingListPromotionTest {

    @Autowired private BookingService bookingService;
    @Autowired private WaitingListService waitingListService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private WaitingListRepository waitingListRepository;

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
    void cancelling_promotesThePeopleWaiting_inOrder() {
        Long ticketTypeId = data.ticketType(2);
        Long owner = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), owner);

        Long first = data.buyer();
        Long second = data.buyer();
        Long third = data.buyer();
        join(ticketTypeId, first, 1);
        join(ticketTypeId, second, 1);
        join(ticketTypeId, third, 1);

        bookingService.cancel(booking.id(), owner); // frees 2 tickets

        assertThat(myEntry(first).status()).isEqualTo(WaitingListStatus.PROMOTED);
        assertThat(myEntry(second).status()).isEqualTo(WaitingListStatus.PROMOTED);
        assertThat(myEntry(third).status()).isEqualTo(WaitingListStatus.WAITING);
        assertThat(myEntry(third).position()).isEqualTo(1L);
        assertThat(stock(ticketTypeId)).isZero();

        List<BookingResponse> firstBookings = bookingService.listMine(first);
        assertThat(firstBookings).hasSize(1);
        assertThat(firstBookings.get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(firstBookings.get(0).ticketCodes()).hasSize(1);
        assertThat(firstBookings.get(0).totalAmount()).isEqualByComparingTo("100.00");
        assertThat(bookingService.listMine(third)).isEmpty();
    }

    @Test
    void aGroupThatDoesNotFitKeepsItsPlace_andSmallerRequestsBehindItAreServed() {
        Long ticketTypeId = data.ticketType(3);
        Long bigOwner = data.buyer();
        Long smallOwner = data.buyer();
        BookingResponse twoSeats = bookingService.book(new BookingRequest(ticketTypeId, 2), bigOwner);
        BookingResponse oneSeat = bookingService.book(new BookingRequest(ticketTypeId, 1), smallOwner);

        Long wantsTwo = data.buyer();
        Long wantsOne = data.buyer();
        join(ticketTypeId, wantsTwo, 2);
        join(ticketTypeId, wantsOne, 1);

        bookingService.cancel(oneSeat.id(), smallOwner); // frees 1 ticket

        assertThat(myEntry(wantsTwo).status()).isEqualTo(WaitingListStatus.WAITING);
        assertThat(myEntry(wantsTwo).position()).isEqualTo(1L);
        assertThat(myEntry(wantsOne).status()).isEqualTo(WaitingListStatus.PROMOTED);
        assertThat(stock(ticketTypeId)).isZero();

        bookingService.cancel(twoSeats.id(), bigOwner); // frees 2 tickets

        assertThat(myEntry(wantsTwo).status()).isEqualTo(WaitingListStatus.PROMOTED);
        assertThat(bookingService.listMine(wantsTwo).get(0).quantity()).isEqualTo(2);
        assertThat(stock(ticketTypeId)).isZero();
    }

    @Test
    void tenCancellationsAtOnce_promoteExactlyTheFirstTenWaiters() throws Exception {
        int seats = 10;
        int waitingCount = 30;
        Long ticketTypeId = data.ticketType(seats);

        List<Long> holders = new ArrayList<>();
        List<Long> holderBookingIds = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            Long holder = data.buyer();
            holders.add(holder);
            holderBookingIds.add(bookingService.book(new BookingRequest(ticketTypeId, 1), holder).id());
        }
        assertThat(stock(ticketTypeId)).isZero();

        List<Long> waiters = new ArrayList<>();
        for (int i = 0; i < waitingCount; i++) {
            Long waiter = data.buyer();
            waiters.add(waiter);
            join(ticketTypeId, waiter, 1);
        }

        ExecutorService pool = Executors.newFixedThreadPool(seats);
        CountDownLatch ready = new CountDownLatch(seats);
        CountDownLatch go = new CountDownLatch(1);
        Queue<String> errors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < seats; i++) {
            Long holder = holders.get(i);
            Long bookingId = holderBookingIds.get(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.cancel(bookingId, holder);
                } catch (RuntimeException e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
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
            softly.assertThat(errors).as("unexpected errors").isEmpty();
            softly.assertThat(stock(ticketTypeId)).as("stock left").isZero();
            softly.assertThat(bookingRepository.countByTicketTypeIdAndStatus(ticketTypeId, BookingStatus.CANCELLED))
                    .as("cancelled bookings").isEqualTo(10L);
            softly.assertThat(bookingRepository.countByTicketTypeIdAndStatus(ticketTypeId, BookingStatus.CONFIRMED))
                    .as("confirmed bookings").isEqualTo(10L);
            softly.assertThat(waitingListRepository.countByTicketTypeIdAndStatus(ticketTypeId, WaitingListStatus.PROMOTED))
                    .as("promoted entries").isEqualTo(10L);
            softly.assertThat(waitingListRepository.countByTicketTypeIdAndStatus(ticketTypeId, WaitingListStatus.WAITING))
                    .as("still waiting").isEqualTo(20L);

            for (int i = 0; i < waitingCount; i++) {
                boolean shouldBePromoted = i < seats;
                softly.assertThat(myEntry(waiters.get(i)).status())
                        .as("waiter %d", i + 1)
                        .isEqualTo(shouldBePromoted ? WaitingListStatus.PROMOTED : WaitingListStatus.WAITING);
                softly.assertThat(bookingService.listMine(waiters.get(i)))
                        .as("bookings of waiter %d", i + 1)
                        .hasSize(shouldBePromoted ? 1 : 0);
            }
        });
    }

    private WaitingListResponse join(Long ticketTypeId, Long userId, int quantity) {
        return waitingListService.join(new WaitingListRequest(ticketTypeId, quantity), userId);
    }

    private WaitingListResponse myEntry(Long userId) {
        return waitingListService.listMine(userId).get(0);
    }

    private int stock(Long ticketTypeId) {
        return ticketTypeRepository.findById(ticketTypeId).orElseThrow().getAvailableQuantity();
    }
}