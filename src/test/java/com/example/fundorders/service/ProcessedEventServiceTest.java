package com.example.fundorders.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.repository.ProcessedEventRepository;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

class ProcessedEventServiceTest {
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
