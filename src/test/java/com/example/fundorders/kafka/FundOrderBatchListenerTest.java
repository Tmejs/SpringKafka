package com.example.fundorders.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.fundorders.exception.InvalidEventException;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.service.FundOrderEventCodec;
import com.example.fundorders.service.ProcessedEventService;

import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.BatchListenerFailedException;
import tools.jackson.databind.json.JsonMapper;

class FundOrderBatchListenerTest {
    private final ProcessedEventService service = mock(ProcessedEventService.class);
    private final FundOrderEventCodec codec = new FundOrderEventCodec(
            JsonMapper.builder().findAndAddModules().build(),
            Validation.buildDefaultValidatorFactory().getValidator());
    private FundOrderBatchListener listener;

    @BeforeEach
    void setUp() {
        listener = new FundOrderBatchListener(codec, service);
    }

    @Test
    void processesValidRecordsInBatchOrder() {
        var first = event();
        var second = event();

        listener.onMessages(List.of(record(0, codec.encode(first)), record(1, codec.encode(second))));

        var order = inOrder(service);
        order.verify(service).process(first);
        order.verify(service).process(second);
        order.verifyNoMoreInteractions();
    }

    @Test
    void identifiesInvalidRecordAndStopsBeforeFollowingRecord() {
        var first = event();
        var last = event();
        var invalid = record(1, "not-json");

        assertThatThrownBy(() -> listener.onMessages(List.of(
                record(0, codec.encode(first)), invalid, record(2, codec.encode(last)))))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, failure -> {
                    assertThat(failure.getRecord()).isSameAs(invalid);
                    assertThat(failure.getCause()).isInstanceOf(InvalidEventException.class);
                });

        var order = inOrder(service);
        order.verify(service).process(first);
        order.verifyNoMoreInteractions();
    }

    @Test
    void identifiesStorageFailureWithoutChangingItsCause() {
        var first = event();
        var failed = record(0, codec.encode(first));
        var outage = new IllegalStateException("database unavailable");
        doThrow(outage).when(service).process(first);

        assertThatThrownBy(() -> listener.onMessages(List.of(failed)))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, failure -> {
                    assertThat(failure.getRecord()).isSameAs(failed);
                    assertThat(failure.getCause()).isSameAs(outage);
                });
    }

    private static ConsumerRecord<String, String> record(long offset, String value) {
        return new ConsumerRecord<>("fund-orders", 0, offset, "order-key", value);
    }

    private static FundOrderStatusEvent event() {
        return new FundOrderStatusEvent(1, UUID.randomUUID(), UUID.randomUUID(), "AGENCY_A",
                "LU1234567890", OrderStatus.ACCEPTED, Instant.parse("2026-10-21T12:00:00Z"));
    }
}
