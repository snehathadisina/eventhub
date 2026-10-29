package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.AttendeeListResponse;
import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.AttendeeService;
import com.eventhub.eventhub_backend.service.BookingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AttendeeListTest {

    @Autowired private AttendeeService attendeeService;
    @Autowired private BookingService bookingService;
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
    void listsTicketsOfConfirmedBookingsOnly_withTheBuyersDetails() {
        Long ticketTypeId = data.ticketType(10);
        Long stays = data.buyer();
        Long leaves = data.buyer();
        bookingService.book(new BookingRequest(ticketTypeId, 2), stays);
        BookingResponse cancelled = bookingService.book(new BookingRequest(ticketTypeId, 3), leaves);
        bookingService.cancel(cancelled.id(), leaves);

        AttendeeListResponse list =
                attendeeService.list(data.eventId(ticketTypeId), data.organizerId(ticketTypeId));

        assertThat(list.totalTickets()).isEqualTo(2L);
        assertThat(list.checkedInCount()).isZero();
        assertThat(list.attendees()).hasSize(2);
        assertThat(list.attendees()).allSatisfy(a -> {
            assertThat(a.attendeeName()).isEqualTo("Buyer");
            assertThat(a.ticketTypeName()).isEqualTo("General");
            assertThat(a.checkedIn()).isFalse();
        });
    }

    @Test
    void anEventWithNoSales_hasAnEmptyList() {
        Long ticketTypeId = data.ticketType(10);

        AttendeeListResponse list =
                attendeeService.list(data.eventId(ticketTypeId), data.organizerId(ticketTypeId));

        assertThat(list.totalTickets()).isZero();
        assertThat(list.attendees()).isEmpty();
    }

    @Test
    void anotherOrganizerCannotSeeTheList() {
        Long ticketTypeId = data.ticketType(10);
        Long stranger = data.otherOrganizer();

        assertRejected(403, () -> attendeeService.list(data.eventId(ticketTypeId), stranger));
    }

    @Test
    void anUnknownEvent_isNotFound() {
        Long ticketTypeId = data.ticketType(10);

        assertRejected(404, () -> attendeeService.list(999_999L, data.organizerId(ticketTypeId)));
    }

    private void assertRejected(int status, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(status));
    }
}