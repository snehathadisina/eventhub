package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Duration DEFAULT_LENGTH = Duration.ofHours(2);

    private final EventRepository eventRepository;

    @Value("${app.timezone:Asia/Kolkata}")
    private String timezone;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** The contents of an .ics calendar file for a published event. */
    public String icsFor(Long eventId) {
        Event event = publishedEvent(eventId);
        String link = frontendUrl + "/events/" + event.getId();

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//EventHub//Events//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("BEGIN:VEVENT");
        // The same UID every time, so importing twice updates the entry instead of duplicating it
        lines.add("UID:event-" + event.getId() + "@eventhub");
        lines.add("DTSTAMP:" + STAMP.format(LocalDateTime.now(ZoneOffset.UTC)));
        lines.add("DTSTART:" + STAMP.format(toUtc(event.getStartTime())));
        lines.add("DTEND:" + STAMP.format(toUtc(endOf(event))));
        lines.add("SUMMARY:" + escape(event.getTitle()));
        lines.add("DESCRIPTION:" + escape(descriptionWithLink(event, link, 2000)));
        String location = locationOf(event);
        if (!location.isEmpty()) {
            lines.add("LOCATION:" + escape(location));
        }
        lines.add("URL:" + link);
        lines.add("STATUS:CONFIRMED");
        // A pop-up reminder one hour before
        lines.add("BEGIN:VALARM");
        lines.add("TRIGGER:-PT1H");
        lines.add("ACTION:DISPLAY");
        lines.add("DESCRIPTION:" + escape("Starting soon: " + event.getTitle()));
        lines.add("END:VALARM");
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        return lines.stream().map(CalendarService::fold).collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    /** A link that opens Google Calendar with the event already filled in. */
    public String googleCalendarUrl(Long eventId) {
        Event event = publishedEvent(eventId);
        String link = frontendUrl + "/events/" + event.getId();
        String start = STAMP.format(toUtc(event.getStartTime()));
        String end = STAMP.format(toUtc(endOf(event)));

        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + enc(event.getTitle())
                + "&dates=" + start + "/" + end
                + "&details=" + enc(descriptionWithLink(event, link, 1000))
                + "&location=" + enc(locationOf(event));
    }

    // ---------- helpers ----------

    private Event publishedEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private LocalDateTime toUtc(LocalDateTime local) {
        return local.atZone(ZoneId.of(timezone)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private LocalDateTime endOf(Event event) {
        LocalDateTime start = event.getStartTime();
        LocalDateTime end = event.getEndTime();
        return end != null && end.isAfter(start) ? end : start.plus(DEFAULT_LENGTH);
    }

    private String locationOf(Event event) {
        return Stream.of(event.getVenue(), event.getCity())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String descriptionWithLink(Event event, String link, int maxLength) {
        String body = event.getDescription() == null ? "" : event.getDescription().trim();
        if (body.length() > maxLength) {
            body = body.substring(0, maxLength - 3) + "...";
        }
        return body.isEmpty() ? "More details: " + link : body + "\n\nMore details: " + link;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * Escapes text for an .ics value. Line breaks become the two characters \n,
     * so an event title can never start a new calendar property.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n");
    }

    /** Lines longer than 75 bytes must be split, with each continuation line starting with a space. */
    static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > 75) {
                out.append("\r\n ");
                octets = 1; // the leading space counts
            }
            out.append(character);
            octets += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}