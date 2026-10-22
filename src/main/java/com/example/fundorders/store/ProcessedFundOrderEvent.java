package com.example.fundorders.store;

import java.time.Instant;
import java.util.UUID;

import com.example.fundorders.event.OrderStatus;

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
