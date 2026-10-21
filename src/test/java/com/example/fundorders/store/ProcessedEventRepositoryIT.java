package com.example.fundorders.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.example.fundorders.event.FundOrderEventCodec;
import com.example.fundorders.event.FundOrderStatusEvent;
import com.example.fundorders.event.InvalidEventException;
import com.example.fundorders.event.OrderStatus;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class ProcessedEventRepositoryIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private ProcessedEventRepository repository;
    private ProcessedEventService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().update("DELETE FROM processed_fund_order_events");
        repository = new ProcessedEventRepository(jdbc);
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
    void rejectsReusedIdWithDifferentContent() {
        var event = event(UUID.randomUUID(), Instant.parse("2026-10-21T12:00:00Z"));
        service.process(event);

        assertThatThrownBy(() -> service.process(new FundOrderStatusEvent(1,
                event.eventId(), event.orderId(), event.partnerCode(), event.fundIsin(),
                OrderStatus.REJECTED, event.occurredAt())))
                .isInstanceOf(InvalidEventException.class);
        assertThat(repository.count()).isEqualTo(1);
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

        assertThat(repository.findPage(PageRequest.of(0, 2)))
                .extracting(ProcessedFundOrderEvent::eventId)
                .containsExactly(newest.eventId(), second.eventId());
        assertThat(repository.findPage(PageRequest.of(1, 2)))
                .extracting(ProcessedFundOrderEvent::eventId)
                .containsExactly(first.eventId());
    }

    private FundOrderStatusEvent event(UUID eventId, Instant occurredAt) {
        return new FundOrderStatusEvent(1, eventId,
                UUID.fromString("72a11fa8-51ec-4496-9f63-6b1a94f78b73"),
                "AGENCY_A", "LU1234567890", OrderStatus.ACCEPTED, occurredAt);
    }
}
