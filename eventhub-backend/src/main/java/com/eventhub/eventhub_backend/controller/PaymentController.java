package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.PaymentRequest;
import com.eventhub.eventhub_backend.dto.PaymentResponse;
import com.eventhub.eventhub_backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/pay")
    public PaymentResponse pay(@PathVariable Long id,
                               @Valid @RequestBody PaymentRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return paymentService.pay(id, Long.valueOf(jwt.getSubject()), request.cardLast4());
    }
}