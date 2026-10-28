package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.WaitingListRequest;
import com.eventhub.eventhub_backend.dto.WaitingListResponse;
import com.eventhub.eventhub_backend.service.WaitingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waiting-list")
@RequiredArgsConstructor
public class WaitingListController {

    private final WaitingListService waitingListService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitingListResponse join(@Valid @RequestBody WaitingListRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return waitingListService.join(request, userId(jwt));
    }

    @GetMapping("/me")
    public List<WaitingListResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return waitingListService.listMine(userId(jwt));
    }

    @DeleteMapping("/{id}")
    public WaitingListResponse leave(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return waitingListService.leave(id, userId(jwt));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}