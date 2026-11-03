package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.ai.EventWriterService;
import com.eventhub.eventhub_backend.dto.EventDraftRequest;
import com.eventhub.eventhub_backend.dto.EventDraftResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/organizer/ai")
@RequiredArgsConstructor
public class AiController {

    private final EventWriterService eventWriterService;

    // Lets the page hide the helper when no API key is set
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", eventWriterService.isEnabled());
    }

    @PostMapping("/event-draft")
    public EventDraftResponse draft(@Valid @RequestBody EventDraftRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return eventWriterService.draft(request, Long.valueOf(jwt.getSubject()));
    }
}