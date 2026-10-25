package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

public record ProcessedFundOrderEvent(
        int schemaVersion,
        UUID eventId,
        UUID orderId,
        String partnerCode,
        OrderStatus status,
        Instant occurredAt,
        Instant processedAt) {
}
