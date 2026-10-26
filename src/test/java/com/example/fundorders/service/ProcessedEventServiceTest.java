package com.example.fundorders.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.fundorders.exception.InvalidEventException;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.repository.ProcessedEventRepository;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

class ProcessedEventServiceTest {
    @Test
    void timestampOutsideSupportedRangeIsRejectedBeforeRepositoryAccess() {
        var repository = mock(ProcessedEventRepository.class);
        var service = new ProcessedEventService(repository, new FundOrderEventCodec(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator()));
        for (var timestamp : List.of(Instant.MAX, Instant.ofEpochMilli(Long.MIN_VALUE))) {
            var event = new FundOrderStatusEvent(1, UUID.randomUUID(), UUID.randomUUID(),
                    "AGENCY_A", OrderStatus.ACCEPTED, timestamp);

            assertThatThrownBy(() -> service.process(event)).as(timestamp.toString())
                    .isInstanceOf(InvalidEventException.class)
                    .hasMessageContaining("occurredAt");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void postgresTimestampOverflowBecomesPermanentEventFailure() {
        var failure = new DataIntegrityViolationException("timestamp rejected",
                new RuntimeException(new SQLException("timestamp out of range", "22008")));

        assertThatThrownBy(() -> serviceFailingWith(failure).process(event()))
                .isInstanceOf(InvalidEventException.class)
                .hasCause(failure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"23505", "23514", "08006"})
    void unrelatedDatabaseErrorsAreNotDiscarded(String sqlState) {
        var failure = new DataIntegrityViolationException("database failure",
                new SQLException("failure", sqlState));

        assertThatThrownBy(() -> serviceFailingWith(failure).process(event())).isSameAs(failure);
    }

    @Test
    void integrityFailureWithoutSqlCauseIsNotDiscarded() {
        var failure = new DataIntegrityViolationException("unknown cause");

        assertThatThrownBy(() -> serviceFailingWith(failure).process(event())).isSameAs(failure);
    }

    private ProcessedEventService serviceFailingWith(RuntimeException failure) {
        var repository = mock(ProcessedEventRepository.class);
        when(repository.insertIfAbsent(event())).thenThrow(failure);
        return new ProcessedEventService(repository, new FundOrderEventCodec(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator()));
    }

    private FundOrderStatusEvent event() {
        return new FundOrderStatusEvent(1,
                UUID.fromString("3c6fc787-e729-46c0-b199-8c8a737c2251"),
                UUID.fromString("72a11fa8-51ec-4496-9f63-6b1a94f78b73"),
                "AGENCY_A", OrderStatus.ACCEPTED, Instant.parse("2026-10-21T12:00:00Z"));
    }

    @Test
    void databaseFailureRemainsRetryable() {
        var repository = mock(ProcessedEventRepository.class);
        var codec = new FundOrderEventCodec(JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        var service = new ProcessedEventService(repository, codec);
        var event = new FundOrderStatusEvent(1, UUID.randomUUID(), UUID.randomUUID(),
                "AGENCY_A", OrderStatus.REJECTED,
                Instant.parse("2026-10-21T12:00:00Z"));
        var outage = new DataAccessResourceFailureException("database unavailable");
        when(repository.insertIfAbsent(event))
                .thenThrow(outage);

        assertThatThrownBy(() -> service.process(event)).isSameAs(outage);
    }
}
