package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CalendarServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 15, 18, 0); // 6 PM in India

    @Autowired private CalendarService calendarService;
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
    void convertsToUtc_andUsesTheEndTime() {
        Long eventId = eventWith("Java Meetup", "Talks and pizza", START, START.plusHours(3));

        String ics = unfold(calendarService.icsFor(eventId));

        assertThat(ics).contains("DTSTART:20300115T123000Z"); // 18:00 India = 12:30 UTC
        assertThat(ics).contains("DTEND:20300115T153000Z");   // 21:00 India = 15:30 UTC
    }

    @Test
    void withoutAnEndTime_theEventLastsTwoHours() {
        Long eventId = eventWith("Java Meetup", "Talks", START, null);

        String ics = unfold(calendarService.icsFor(eventId));

        assertThat(ics).contains("DTEND:20300115T143000Z");
    }

    @Test
    void hasTheStructureCalendarAppsExpect() {
        Long eventId = eventWith("Java Meetup", "Talks", START, null);

        String ics = calendarService.icsFor(eventId);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("VERSION:2.0\r\n", "BEGIN:VEVENT\r\n", "END:VEVENT\r\n",
                "UID:event-" + eventId + "@eventhub\r\n", "LOCATION:Main Hall\\, Hyderabad\r\n");
    }

    @Test
    void specialCharactersAreEscaped_andCannotInjectNewProperties() {
        Long eventId = eventWith("Rock, Pop; Jazz\\Night\nATTENDEE:mailto:evil@example.com",
                "Line one\nLine two", START, null);

        String ics = unfold(calendarService.icsFor(eventId));

        assertThat(ics).contains(
                "SUMMARY:Rock\\, Pop\\; Jazz\\\\Night\\nATTENDEE:mailto:evil@example.com");
        assertThat(ics.split("\r\n")).noneMatch(line -> line.startsWith("ATTENDEE:"));
    }

    @Test
    void longLinesAreFolded_withoutLosingText() {
        String description = "é".repeat(300); // 2 bytes per character
        Long eventId = eventWith("Java Meetup", description, START, null);

        String ics = calendarService.icsFor(eventId);

        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(unfold(ics)).contains(description);
    }

    @Test
    void theGoogleLinkIsEncodedAndUsesUtcTimes() {
        Long eventId = eventWith("C++ & Friends", "Talks", START, START.plusHours(3));

        String url = calendarService.googleCalendarUrl(eventId);

        assertThat(url).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE");
        assertThat(url).contains("text=C%2B%2B+%26+Friends");
        assertThat(url).contains("dates=20300115T123000Z/20300115T153000Z");
        assertThat(url).contains("location=Main+Hall%2C+Hyderabad");
    }

    @Test
    void aCancelledOrUnknownEvent_isNotFound() {
        Long eventId = eventWith("Java Meetup", "Talks", START, null);
        Event event = eventRepository.findById(eventId).orElseThrow();
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);

        assertNotFound(() -> calendarService.icsFor(eventId));
        assertNotFound(() -> calendarService.googleCalendarUrl(eventId));
        assertNotFound(() -> calendarService.icsFor(999_999L));
    }

    private Long eventWith(String title, String description, LocalDateTime start, LocalDateTime end) {
        Long ticketTypeId = data.ticketType(5);
        Event event = eventRepository.findById(data.eventId(ticketTypeId)).orElseThrow();
        event.setTitle(title);
        event.setDescription(description);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setVenue("Main Hall");
        event.setCity("Hyderabad");
        eventRepository.save(event);
        return event.getId();
    }

    /** Folded lines are rejoined, so we can search for whole values. */
    private String unfold(String ics) {
        return ics.replace("\r\n ", "");
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
}