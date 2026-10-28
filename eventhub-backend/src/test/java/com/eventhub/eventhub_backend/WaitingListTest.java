package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.dto.WaitingListResponse;
import com.eventhub.eventhub_backend.enums.WaitingListStatus;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.BookingService;
import com.eventhub.eventhub_backend.service.WaitingListService;
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
class WaitingListTest {

    @Autowired private BookingService bookingService;
    @Autowired private WaitingListService waitingListService;
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
    void joiningASoldOutTicketType_startsAtPositionOne() {
        Long ticketTypeId = soldOutTicketType(2);
        Long waiting = data.buyer();

        WaitingListResponse entry = waitingListService.join(new WaitingListRequest(ticketTypeId, 1), waiting);

        assertThat(entry.status()).isEqualTo(WaitingListStatus.WAITING);
        assertThat(entry.position()).isEqualTo(1L);
    }

    @Test
    void positionsFollowJoinOrder_andMoveUpWhenSomeoneLeaves() {
        Long ticketTypeId = soldOutTicketType(2);
        Long first = data.buyer();
        Long second = data.buyer();
        Long third = data.buyer();

        WaitingListResponse firstEntry = waitingListService.join(new WaitingListRequest(ticketTypeId, 1), first);
        WaitingListResponse secondEntry = waitingListService.join(new WaitingListRequest(ticketTypeId, 1), second);
        WaitingListResponse thirdEntry = waitingListService.join(new WaitingListRequest(ticketTypeId, 1), third);

        assertThat(firstEntry.position()).isEqualTo(1L);
        assertThat(secondEntry.position()).isEqualTo(2L);
        assertThat(thirdEntry.position()).isEqualTo(3L);

        waitingListService.leave(firstEntry.id(), first);

        assertThat(waitingListService.listMine(second).get(0).position()).isEqualTo(1L);
        assertThat(waitingListService.listMine(third).get(0).position()).isEqualTo(2L);
        assertThat(waitingListService.listMine(first).get(0).status()).isEqualTo(WaitingListStatus.CANCELLED);
        assertThat(waitingListService.listMine(first).get(0).position()).isNull();
    }

    @Test
    void cannotJoinWhileTicketsAreAvailable() {
        Long ticketTypeId = data.ticketType(5);
        Long buyer = data.buyer();

        assertRejected(409, () -> waitingListService.join(new WaitingListRequest(ticketTypeId, 1), buyer));
    }

    @Test
    void cannotJoinTwiceForTheSameTicketType() {
        Long ticketTypeId = soldOutTicketType(2);
        Long buyer = data.buyer();
        waitingListService.join(new WaitingListRequest(ticketTypeId, 1), buyer);

        assertRejected(409, () -> waitingListService.join(new WaitingListRequest(ticketTypeId, 1), buyer));
    }

    @Test
    void cannotWaitForMoreTicketsThanExistInTotal() {
        Long ticketTypeId = soldOutTicketType(2);
        Long buyer = data.buyer();

        assertRejected(400, () -> waitingListService.join(new WaitingListRequest(ticketTypeId, 3), buyer));
    }

    @Test
    void onlyTheOwnerCanLeave_andOnlyOnce() {
        Long ticketTypeId = soldOutTicketType(2);
        Long owner = data.buyer();
        Long stranger = data.buyer();
        WaitingListResponse entry = waitingListService.join(new WaitingListRequest(ticketTypeId, 1), owner);

        assertRejected(404, () -> waitingListService.leave(entry.id(), stranger));

        assertThat(waitingListService.leave(entry.id(), owner).status())
                .isEqualTo(WaitingListStatus.CANCELLED);
        assertRejected(409, () -> waitingListService.leave(entry.id(), owner));
    }

    /** Creates a ticket type and sells every ticket, so the stock is zero. */
    private Long soldOutTicketType(int capacity) {
        Long ticketTypeId = data.ticketType(capacity);
        bookingService.book(new BookingRequest(ticketTypeId, capacity), data.buyer());
        return ticketTypeId;
    }

    private void assertRejected(int status, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(status));
    }
}