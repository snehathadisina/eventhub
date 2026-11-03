package com.eventhub.eventhub_backend.dto;

import java.math.BigDecimal;

public record EventDraftResponse(
        String title,
        String description,
        String category,
        boolean free,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String priceNote
) {}