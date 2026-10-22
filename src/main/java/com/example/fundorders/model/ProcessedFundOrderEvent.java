package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

public record ProcessedFundOrderEvent(
        int schemaVersion,
        UUID eventId,
        UUID orderId,
        String partnerCode,
        String fundIsin,
        OrderStatus status,
        Instant occurredAt,
        Instant processedAt) {
}
