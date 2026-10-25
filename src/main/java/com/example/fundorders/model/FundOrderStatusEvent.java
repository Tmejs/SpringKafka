package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FundOrderStatusEvent(
        int schemaVersion,
        @NotNull UUID eventId,
        @NotNull UUID orderId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,32}") String partnerCode,
        @NotNull OrderStatus status,
        @NotNull Instant occurredAt) {
}
