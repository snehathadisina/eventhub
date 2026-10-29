package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.AttendeeListResponse;
import com.eventhub.eventhub_backend.service.AttendeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizer/events/{eventId}")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    @GetMapping("/attendees")
    public AttendeeListResponse attendees(@PathVariable Long eventId,
                                          @AuthenticationPrincipal Jwt jwt) {
        return attendeeService.list(eventId, Long.valueOf(jwt.getSubject()));
    }
}