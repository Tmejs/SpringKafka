package com.example.fundorders.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.model.ProcessedFundOrderEvent;
import com.example.fundorders.service.FundOrderEventCodec;
import com.example.fundorders.service.ProcessedEventService;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@DataJpaTest
class ProcessedEventRepositoryIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProcessedEventRepository repository;
    private ProcessedEventService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        service = new ProcessedEventService(repository,
                new FundOrderEventCodec(JsonMapper.builder().findAndAddModules().build(),
                        Validation.buildDefaultValidatorFactory().getValidator()));
    }

    @Test
    void persistsOneEventAndIgnoresIdenticalReplay() {
        var event = event(UUID.fromString("3c6fc787-e729-46c0-b199-8c8a737c2251"),
                Instant.parse("2026-10-21T12:00:00Z"));

        service.process(event);
        service.process(event);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByEventId(event.eventId()).orElseThrow().status())
                .isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void ignoresReusedIdWithDifferentContentAndPreservesOriginalEvent() {
        var event = event(UUID.randomUUID(), Instant.parse("2026-10-21T12:00:00Z"));
        service.process(event);
        var original = repository.findByEventId(event.eventId()).orElseThrow();

        service.process(new FundOrderStatusEvent(1,
                event.eventId(), event.orderId(), event.partnerCode(), event.fundIsin(),
                OrderStatus.REJECTED, event.occurredAt()));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByEventId(event.eventId())).contains(original);
    }

    @Test
    void pagesNewestOccurrenceFirstWithEventIdTieBreak() {
        var first = event(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-10-21T12:00:00Z"));
        var second = event(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                first.occurredAt());
        var newest = event(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                Instant.parse("2026-10-21T13:00:00Z"));
        service.process(first);
        service.process(second);
        service.process(newest);

        assertThat(repository.findPage(PageRequest.of(0, 2)).getContent())
                .extracting(ProcessedFundOrderEvent::eventId)
                .containsExactly(newest.eventId(), second.eventId());
        assertThat(repository.findPage(PageRequest.of(1, 2)).getContent())
                .extracting(ProcessedFundOrderEvent::eventId)
                .containsExactly(first.eventId());
    }

    private FundOrderStatusEvent event(UUID eventId, Instant occurredAt) {
        return new FundOrderStatusEvent(1, eventId,
                UUID.fromString("72a11fa8-51ec-4496-9f63-6b1a94f78b73"),
                "AGENCY_A", "LU1234567890", OrderStatus.ACCEPTED, occurredAt);
    }
}
