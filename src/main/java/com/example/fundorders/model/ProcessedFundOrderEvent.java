package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"schemaVersion", "eventId", "orderId", "partnerCode", "status", "occurredAt", "processedAt"})
public record ProcessedFundOrderEvent(
        int schemaVersion,
        UUID eventId,
        UUID orderId,
        String partnerCode,
        OrderStatus status,
        Instant occurredAt,
        Instant processedAt) {
}
