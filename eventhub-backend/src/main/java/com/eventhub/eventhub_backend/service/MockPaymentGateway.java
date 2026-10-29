package com.eventhub.eventhub_backend.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGateway {

    public record Result(boolean success, String reference, String message) {}

    // A real gateway would charge `amount`. The mock only looks at the last 4 digits.
    public Result charge(String cardLast4, BigDecimal amount) {
        return switch (cardLast4) {
            case "4242" -> new Result(true, newReference("MOCK"), "Payment successful");
            case "0002" -> new Result(false, null, "Your card was declined");
            case "9995" -> new Result(false, null, "Your card has insufficient funds");
            default -> new Result(false, null, "Mock gateway: use a test card ending in 4242");
        };
    }

    public String newReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}