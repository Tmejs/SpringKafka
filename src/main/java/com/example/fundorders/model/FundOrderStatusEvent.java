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
        @NotBlank String partnerCode,
        @NotNull OrderStatus status,
        @NotNull Instant occurredAt) {
}
