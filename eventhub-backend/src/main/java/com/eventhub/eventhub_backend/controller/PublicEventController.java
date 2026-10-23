package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.EventDetailResponse;
import com.eventhub.eventhub_backend.dto.PageResponse;
import com.eventhub.eventhub_backend.dto.PublicEventResponse;
import com.eventhub.eventhub_backend.service.PublicEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final PublicEventService publicEventService;

    @GetMapping
    public PageResponse<PublicEventResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return publicEventService.search(q, city, category, from, to, page, size);
    }

    @GetMapping("/{id}")
    public EventDetailResponse detail(@PathVariable Long id) {
        return publicEventService.getDetail(id);
    }
}