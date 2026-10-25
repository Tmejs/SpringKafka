package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_fund_order_events")
public class ProcessedEventEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "partner_code", nullable = false, length = 32)
    private String partnerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedFundOrderEvent toModel() {
        return new ProcessedFundOrderEvent(schemaVersion, eventId, orderId, partnerCode,
                status, occurredAt, processedAt);
    }
}
