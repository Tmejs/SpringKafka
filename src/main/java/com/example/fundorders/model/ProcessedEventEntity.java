package com.example.fundorders.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "fund_isin", nullable = false, length = 12)
    private String fundIsin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    protected ProcessedEventEntity() {
    }

    public String payloadHash() {
        return payloadHash;
    }

    public ProcessedFundOrderEvent toModel() {
        return new ProcessedFundOrderEvent(schemaVersion, eventId, orderId, partnerCode,
                fundIsin, status, occurredAt, processedAt);
    }
}
