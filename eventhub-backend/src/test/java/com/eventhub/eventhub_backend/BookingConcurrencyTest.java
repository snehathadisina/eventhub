package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
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
@TestPropertySource(properties = "app.booking.stock-strategy=lock")
class BookingConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TicketRepository ticketRepository;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${app.booking.stock-strategy}")
    private String strategy;

    private record Outcome(int succeeded, int soldOut, List<String> otherErrors) {}

    @BeforeEach
    void onlyRunAgainstTheTestDatabase() {
        assertThat(datasourceUrl)
                .as("Refusing to run: this test must only use the eventhub_test database")
                .contains("eventhub_test");
    }

    @Test
    void fiftyBuyersCompeteForTenTickets_neverSellMoreThanTen() throws Exception {
        runScenario("50 buyers, 10 tickets, 1 each", 10, 50, 1, 10);
    }

    @Test
    void enoughTicketsForEveryone_nobodyIsWronglyRejected() throws Exception {
        runScenario("40 buyers, 40 tickets, 1 each", 40, 40, 1, 40);
    }

    @Test
    void hundredBuyersWantTwoTicketsEach_neverOversell() throws Exception {
        // 25 tickets, 2 per buyer: exactly 12 buyers fit (24 tickets) and 1 ticket stays unsold
        runScenario("100 buyers, 25 tickets, 2 each", 25, 100, 2, 12);
    }

    @Test
    void tooManyTicketsRequested_isRejectedAndChangesNothing() {
        Long ticketTypeId = createTicketType(3);
        Long buyerId = createBuyers(1).get(0);

        assertThatThrownBy(() -> bookingService.book(new BookingRequest(ticketTypeId, 5), buyerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));

        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getAvailableQuantity())
                .isEqualTo(3);
        assertThat(bookingRepository.countByTicketTypeId(ticketTypeId)).isZero();
        assertThat(ticketRepository.countByTicketTypeId(ticketTypeId)).isZero();
    }

    private void runScenario(String name, int capacity, int buyers, int quantityEach,
                             int expectedSuccesses) throws Exception {
        Long ticketTypeId = createTicketType(capacity);
        List<Long> buyerIds = createBuyers(buyers);

        Outcome outcome = runConcurrently(ticketTypeId, buyerIds, quantityEach);

        int availableAfter = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow().getAvailableQuantity();
        long bookingsInDb = bookingRepository.countByTicketTypeId(ticketTypeId);
        long ticketsInDb = ticketRepository.countByTicketTypeId(ticketTypeId);
        int expectedLeft = capacity - expectedSuccesses * quantityEach;

        System.out.println("""

                ========== CONCURRENCY REPORT ==========
                Strategy ..................... %s
                Scenario ..................... %s
                Bookings that succeeded ...... %d   (expected %d)
                Rejected as sold out (409) ... %d   (expected %d)
                Other failures ............... %d
                Bookings saved in database ... %d
                Ticket rows saved ............ %d   (expected %d)
                Stock counter says left ...... %d   (expected %d)
                ========================================
                """.formatted(strategy, name,
                outcome.succeeded(), expectedSuccesses,
                outcome.soldOut(), buyers - expectedSuccesses,
                outcome.otherErrors().size(),
                bookingsInDb,
                ticketsInDb, expectedSuccesses * quantityEach,
                availableAfter, expectedLeft));

        assertSoftly(softly -> {
            softly.assertThat(outcome.otherErrors()).as("unexpected errors").isEmpty();
            softly.assertThat(outcome.succeeded()).as("successful bookings")
                    .isEqualTo(expectedSuccesses);
            softly.assertThat(outcome.soldOut()).as("rejected as sold out")
                    .isEqualTo(buyers - expectedSuccesses);
            softly.assertThat(bookingsInDb).as("bookings saved")
                    .isEqualTo((long) expectedSuccesses);
            softly.assertThat(ticketsInDb).as("ticket rows saved")
                    .isEqualTo((long) expectedSuccesses * quantityEach);
            softly.assertThat(availableAfter).as("stock counter")
                    .isEqualTo(expectedLeft);
        });
    }

    private Outcome runConcurrently(Long ticketTypeId, List<Long> buyerIds, int quantityEach)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(buyerIds.size());
        CountDownLatch ready = new CountDownLatch(buyerIds.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        Queue<String> otherErrors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (Long buyerId : buyerIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(); // every buyer waits here until the starting gun
                try {
                    bookingService.book(new BookingRequest(ticketTypeId, quantityEach), buyerId);
                    succeeded.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) {
                        soldOut.incrementAndGet();
                    } else {
                        otherErrors.add(String.valueOf(e.getMessage()));
                    }
                } catch (RuntimeException e) {
                    otherErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return null;
            }));
        }

        ready.await();   // all threads are lined up
        go.countDown();  // fire
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        return new Outcome(succeeded.get(), soldOut.get(), List.copyOf(otherErrors));
    }

    private Long createTicketType(int capacity) {
        User organizer = newUser("Test Organizer", Role.ORGANIZER);
        userRepository.save(organizer);

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle("Concurrency Test Event");
        event.setStartTime(LocalDateTime.now().plusDays(30));
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);

        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName("General");
        ticketType.setPrice(new BigDecimal("100.00"));
        ticketType.setTotalQuantity(capacity);
        ticketType.setAvailableQuantity(capacity);
        return ticketTypeRepository.save(ticketType).getId();
    }

    private List<Long> createBuyers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(userRepository.save(newUser("Buyer " + i, Role.ATTENDEE)).getId());
        }
        return ids;
    }

    private User newUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPasswordHash("not-used-in-this-test");
        user.setRole(role);
        return user;
    }
}