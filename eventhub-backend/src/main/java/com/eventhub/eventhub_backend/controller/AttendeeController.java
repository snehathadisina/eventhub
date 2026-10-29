package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.AttendeeListResponse;
import com.eventhub.eventhub_backend.dto.CheckInRequest;
import com.eventhub.eventhub_backend.dto.CheckInResponse;
import com.eventhub.eventhub_backend.service.AttendeeService;
import com.eventhub.eventhub_backend.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizer/events/{eventId}")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;
    private final CheckInService checkInService;

    @GetMapping("/attendees")
    public AttendeeListResponse attendees(@PathVariable Long eventId,
                                          @AuthenticationPrincipal Jwt jwt) {
        return attendeeService.list(eventId, organizerId(jwt));
    }

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@PathVariable Long eventId,
                                   @Valid @RequestBody CheckInRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return checkInService.checkIn(eventId, request.ticketCode(), organizerId(jwt));
    }

    private Long organizerId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}