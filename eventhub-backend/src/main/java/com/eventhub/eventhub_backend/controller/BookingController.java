package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.BookingRequest;
import com.eventhub.eventhub_backend.dto.BookingResponse;
import com.eventhub.eventhub_backend.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookingRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return bookingService.book(request, userId(jwt));
    }

    @GetMapping("/me")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.listMine(userId(jwt));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.cancel(id, userId(jwt));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}