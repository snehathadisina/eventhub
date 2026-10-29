package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.dto.CheckInResponse;
import com.eventhub.eventhub_backend.dto.TicketInfo;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.AttendeeService;
import com.eventhub.eventhub_backend.service.BookingService;
import com.eventhub.eventhub_backend.service.CheckInService;
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
class CheckInTest {

    @Autowired private BookingService bookingService;
    @Autowired private CheckInService checkInService;
    @Autowired private AttendeeService attendeeService;
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
    }

    @Test
    void aValidTicketIsCheckedIn_andTheCountersGoUp() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        List<String> codes = bookingService.book(new BookingRequest(ticketTypeId, 2), buyer).ticketCodes();

        CheckInResponse response = checkInService.checkIn(
                data.eventId(ticketTypeId), codes.get(0), data.organizerId(ticketTypeId));

        assertThat(response.attendeeName()).isEqualTo("Buyer");
        assertThat(response.ticketTypeName()).isEqualTo("General");
        assertThat(response.checkedInCount()).isEqualTo(1L);
        assertThat(response.totalTickets()).isEqualTo(2L);

        List<TicketInfo> tickets = bookingService.listMine(buyer).get(0).tickets();
        assertThat(tickets)
                .filteredOn(TicketInfo::checkedIn)
                .extracting(TicketInfo::code)
                .containsExactly(codes.get(0));
    }

    @Test
    void theSameTicketTwice_isRejected() {
        Long ticketTypeId = data.ticketType(5);
        String code = bookingService.book(new BookingRequest(ticketTypeId, 1), data.buyer())
                .ticketCodes().get(0);
        Long eventId = data.eventId(ticketTypeId);
        Long organizer = data.organizerId(ticketTypeId);

        checkInService.checkIn(eventId, code, organizer);

        assertRejected(409, "Already checked in", () -> checkInService.checkIn(eventId, code, organizer));
    }

    @Test
    void aCancelledBooking_cannotCheckIn() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 1), buyer);
        bookingService.cancel(booking.id(), buyer);

        assertRejected(409, "cancelled", () -> checkInService.checkIn(
                data.eventId(ticketTypeId), booking.ticketCodes().get(0), data.organizerId(ticketTypeId)));
    }

    @Test
    void aTicketFromAnotherEvent_isRejected() {
        Long firstEvent = data.ticketType(5);
        Long secondEvent = data.ticketType(5);
        String codeOfSecondEvent = bookingService.book(new BookingRequest(secondEvent, 1), data.buyer())
                .ticketCodes().get(0);

        assertRejected(409, "different event", () -> checkInService.checkIn(
                data.eventId(firstEvent), codeOfSecondEvent, data.organizerId(firstEvent)));
    }

    @Test
    void anUnknownCode_isNotFound() {
        Long ticketTypeId = data.ticketType(5);

        assertRejected(404, "not found", () -> checkInService.checkIn(
                data.eventId(ticketTypeId), "no-such-code", data.organizerId(ticketTypeId)));
    }

    @Test
    void anotherOrganizerCannotCheckInTickets() {
        Long ticketTypeId = data.ticketType(5);
        String code = bookingService.book(new BookingRequest(ticketTypeId, 1), data.buyer())
                .ticketCodes().get(0);
        Long stranger = data.otherOrganizer();

        assertRejected(403, "own events", () -> checkInService.checkIn(
                data.eventId(ticketTypeId), code, stranger));
    }

    @Test
    void aCancelledEvent_isNotOpenForCheckIn() {
        Long ticketTypeId = data.ticketType(5);
        String code = bookingService.book(new BookingRequest(ticketTypeId, 1), data.buyer())
                .ticketCodes().get(0);
        data.cancelEvent(ticketTypeId);

        assertRejected(409, "not open", () -> checkInService.checkIn(
                data.eventId(ticketTypeId), code, data.organizerId(ticketTypeId)));
    }

    @Test
    void aBookingWithAUsedTicket_cannotBeCancelled() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();
        BookingResponse booking = bookingService.book(new BookingRequest(ticketTypeId, 2), buyer);
        checkInService.checkIn(data.eventId(ticketTypeId), booking.ticketCodes().get(0),
                data.organizerId(ticketTypeId));

        assertRejected(409, "already used", () -> bookingService.cancel(booking.id(), buyer));

        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getAvailableQuantity())
                .isEqualTo(3);
    }

    @Test
    void twentyScannersOneTicket_exactlyOneWins() throws Exception {
        Long ticketTypeId = data.ticketType(5);
        String code = bookingService.book(new BookingRequest(ticketTypeId, 1), data.buyer())
                .ticketCodes().get(0);
        Long eventId = data.eventId(ticketTypeId);
        Long organizer = data.organizerId(ticketTypeId);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Queue<String> otherErrors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    checkInService.checkIn(eventId, code, organizer);
                    succeeded.incrementAndGet();
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
            softly.assertThat(succeeded.get()).as("successful check-ins").isEqualTo(1);
            softly.assertThat(rejected.get()).as("rejected as already used").isEqualTo(threads - 1);
            softly.assertThat(attendeeService.list(eventId, organizer).checkedInCount())
                    .as("checked-in count").isEqualTo(1L);
        });
    }

    private void assertRejected(int status, String messagePart, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(status);
            assertThat(e.getReason()).containsIgnoringCase(messagePart);
        });
    }
}