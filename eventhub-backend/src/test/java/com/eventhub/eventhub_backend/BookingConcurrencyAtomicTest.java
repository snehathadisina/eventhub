package com.eventhub.eventhub_backend;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.booking.stock-strategy=atomic")
class BookingConcurrencyAtomicTest extends BookingConcurrencyTest {
}