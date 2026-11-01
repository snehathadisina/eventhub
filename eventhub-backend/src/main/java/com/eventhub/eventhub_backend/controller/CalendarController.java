package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/calendar.ics")
    public ResponseEntity<byte[]> ics(@PathVariable Long eventId) {
        byte[] body = calendarService.icsFor(eventId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("eventhub-event-" + eventId + ".ics").build().toString())
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/google-calendar")
    public ResponseEntity<Void> google(@PathVariable Long eventId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(calendarService.googleCalendarUrl(eventId)))
                .build();
    }
}