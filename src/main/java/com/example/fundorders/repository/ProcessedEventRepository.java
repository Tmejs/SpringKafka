package com.example.fundorders.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.ProcessedEventEntity;
import com.example.fundorders.model.ProcessedFundOrderEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO processed_fund_order_events
                (event_id, schema_version, order_id, partner_code, fund_isin,
                 status, occurred_at, payload_hash)
            VALUES (:eventId, :schemaVersion, :orderId, :partnerCode, :fundIsin,
                    :status, :occurredAt, :payloadHash)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
            @Param("schemaVersion") int schemaVersion,
            @Param("orderId") UUID orderId,
            @Param("partnerCode") String partnerCode,
            @Param("fundIsin") String fundIsin,
            @Param("status") String status,
            @Param("occurredAt") Instant occurredAt,
            @Param("payloadHash") String payloadHash);

    default boolean insertIfAbsent(FundOrderStatusEvent event, String payloadHash) {
        return insertIfAbsent(event.eventId(), event.schemaVersion(), event.orderId(),
                event.partnerCode(), event.fundIsin(), event.status().name(),
                event.occurredAt(), payloadHash) == 1;
    }

    default Optional<String> findPayloadHash(UUID eventId) {
        return findById(eventId).map(ProcessedEventEntity::payloadHash);
    }

    default Optional<ProcessedFundOrderEvent> findByEventId(UUID eventId) {
        return findById(eventId).map(ProcessedEventEntity::toModel);
    }

    default Page<ProcessedFundOrderEvent> findPage(Pageable pageable) {
        var order = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("eventId"));
        var orderedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);
        return findAll(orderedPage).map(ProcessedEventEntity::toModel);
    }
}
