package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FundOrderStatusEvent(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"1"}) int schemaVersion,
        @NotNull UUID eventId,
        @NotNull UUID orderId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,32}$") String partnerCode,
        @NotNull OrderStatus status,
        @Schema(description = "ISO-8601 instant from 0001-01-01T00:00:00Z through 9999-12-31T23:59:59.999999Z, inclusive. "
                + "The range applies in UTC. Out-of-range typed requests return 400; Kafka events go to the DLQ. "
                + "Stored timestamps have microsecond precision.", example = "2026-10-26T12:30:00Z")
        @NotNull Instant occurredAt) {
}
