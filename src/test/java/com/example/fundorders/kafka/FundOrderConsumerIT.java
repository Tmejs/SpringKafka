package com.example.fundorders.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.repository.ProcessedEventRepository;
import com.example.fundorders.support.MessagingIntegrationTestSupport;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.KafkaHeaders;

class FundOrderConsumerIT extends MessagingIntegrationTestSupport {
    private static final int PARTITION = 1;

    @Autowired
    ProcessedEventRepository repository;

    @Test
    void directlyPublishedEventIsStoredInPostgres() throws Exception {
        var eventId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        try (var producer = directProducer()) {
            var metadata = producer.send(new ProducerRecord<>(SOURCE, PARTITION, orderId.toString(),
                    eventJson(eventId, orderId, "REJECTED"))).get(10, TimeUnit.SECONDS);
            assertThat(metadata.topic()).isEqualTo(SOURCE);
            assertThat(metadata.partition()).isEqualTo(PARTITION);
        }

        assertStored(eventId, orderId, OrderStatus.REJECTED);
    }

    @ParameterizedTest(name = "directly published {0} reaches the DLQ")
    @MethodSource("invalidMessages")
    void invalidKafkaMessageReachesDlqAndFollowingEventStillPersists(String description,
            UUID invalidId, String invalidBody) throws Exception {
        var nextId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var firstDlqOffset = endOffset(DLQ, PARTITION);

        try (var consumer = recordConsumer(DLQ, PARTITION, firstDlqOffset);
             var producer = directProducer()) {
            var source = producer.send(new ProducerRecord<>(SOURCE, PARTITION, orderId.toString(), invalidBody))
                    .get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(SOURCE, PARTITION, orderId.toString(),
                    eventJson(nextId, orderId, "EXECUTED"))).get(10, TimeUnit.SECONDS);

            var deadLetter = awaitRecord(consumer, item -> invalidBody.equals(item.value()));
            assertThat(deadLetter.topic()).isEqualTo(DLQ);
            assertThat(deadLetter.partition()).isEqualTo(PARTITION);
            assertThat(deadLetter.key()).isEqualTo(orderId.toString());
            assertThat(deadLetter.value()).isEqualTo(invalidBody);
            var headers = deadLetter.headers();
            assertThat(new String(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    StandardCharsets.UTF_8)).isEqualTo(SOURCE);
            assertThat(ByteBuffer.wrap(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION).value()).getInt())
                    .isEqualTo(source.partition());
            assertThat(ByteBuffer.wrap(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value()).getLong())
                    .isEqualTo(source.offset());
            assertThat(headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        }

        assertStored(nextId, orderId, OrderStatus.EXECUTED);
        assertThat(repository.findByEventId(invalidId)).isEmpty();
    }

    private static Stream<Arguments> invalidMessages() {
        var malformedId = UUID.randomUUID();
        var invalidId = UUID.randomUUID();
        return Stream.of(
                Arguments.of("malformed JSON", malformedId,
                        "{\"eventId\":\"" + malformedId + "\",broken-json}"),
                Arguments.of("unsupported schema version", invalidId,
                        eventJson(invalidId, UUID.randomUUID(), "ACCEPTED")
                                .replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
    }

    private void assertStored(UUID eventId, UUID orderId, OrderStatus status) {
        await().atMost(WAIT).untilAsserted(() -> {
            var result = repository.findByEventId(eventId);
            assertThat(result).isPresent();
            var stored = result.orElseThrow();
            assertThat(stored.eventId()).isEqualTo(eventId);
            assertThat(stored.schemaVersion()).isEqualTo(1);
            assertThat(stored.orderId()).isEqualTo(orderId);
            assertThat(stored.partnerCode()).isEqualTo("AGENCY_A");
            assertThat(stored.status()).isEqualTo(status);
            assertThat(stored.occurredAt())
                    .isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
            assertThat(stored.processedAt()).isNotNull();
        });
    }
}
