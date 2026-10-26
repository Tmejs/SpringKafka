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
        @Schema(description = "Parsed as a Java Instant. PostgreSQL datetime overflow is sent to the DLQ during consumption; stored timestamps have microsecond precision.")
        @NotNull Instant occurredAt) {
}
