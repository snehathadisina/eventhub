package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.entity.TicketType;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.repository.BookingRepository;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int BUYERS = 50;

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private BookingRepository bookingRepository;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @BeforeEach
    void onlyRunAgainstTheTestDatabase() {
        assertThat(datasourceUrl)
                .as("Refusing to run: this test must only use the eventhub_test database")
                .contains("eventhub_test");
    }

    @Test
    void fiftyBuyersCompeteForTenTickets_neverSellMoreThanTen() throws Exception {
        Long ticketTypeId = createTicketType(CAPACITY);
        List<Long> buyerIds = createBuyers(BUYERS);

        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch ready = new CountDownLatch(BUYERS);
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
                    bookingService.book(new BookingRequest(ticketTypeId, 1), buyerId);
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

        ready.await();   // all 50 threads are lined up
        go.countDown();  // fire
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        int availableAfter = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow().getAvailableQuantity();
        long bookingsInDb = bookingRepository.countByTicketTypeId(ticketTypeId);

        System.out.println("""

                ========== CONCURRENCY REPORT ==========
                Tickets that exist ........... %d
                Buyers competing ............. %d
                Bookings that succeeded ...... %d
                Rejected as sold out (409) ... %d
                Other failures ............... %d
                Bookings saved in database ... %d
                Stock counter says left ...... %d   (should be %d)
                ========================================
                """.formatted(CAPACITY, BUYERS, succeeded.get(), soldOut.get(),
                otherErrors.size(), bookingsInDb, availableAfter,
                Math.max(0, CAPACITY - bookingsInDb)));

        assertSoftly(softly -> {
            softly.assertThat(otherErrors).as("unexpected errors").isEmpty();
            softly.assertThat(bookingsInDb)
                    .as("OVERSOLD: only %d tickets exist but %d bookings were saved",
                            CAPACITY, bookingsInDb)
                    .isEqualTo((long) CAPACITY);
            softly.assertThat(succeeded.get())
                    .as("successful bookings must match saved bookings")
                    .isEqualTo((int) bookingsInDb);
            softly.assertThat(availableAfter)
                    .as("stock counter must equal capacity minus bookings")
                    .isEqualTo(CAPACITY - (int) bookingsInDb);
        });
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