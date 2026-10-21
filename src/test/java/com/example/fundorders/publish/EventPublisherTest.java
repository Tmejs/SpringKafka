package com.example.fundorders.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.example.fundorders.event.FundOrderEventCodec;
import com.example.fundorders.event.FundOrderStatusEvent;
import com.example.fundorders.event.OrderStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class EventPublisherTest {
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final FundOrderEventCodec codec = mock(FundOrderEventCodec.class);
    private final EventPublisher publisher = new EventPublisher(template, codec, "fund-orders", Duration.ofSeconds(2));

    @Test
    void rawPublicationPreservesMalformedText() {
        givenAcknowledgment();

        var receipt = publisher.publishRaw("{not-json");

        var record = sentRecord();
        assertThat(record.topic()).isEqualTo("fund-orders");
        assertThat(record.value()).isEqualTo("{not-json");
        assertThat(record.key()).isNotBlank();
        assertThat(receipt.offset()).isEqualTo(42L);
    }

    @Test
    void typedPublicationUsesOrderIdAsKey() {
        givenAcknowledgment();
        var event = new FundOrderStatusEvent(1, UUID.randomUUID(),
                UUID.fromString("72a11fa8-51ec-4496-9f63-6b1a94f78b73"),
                "AGENCY_A", "LU1234567890", OrderStatus.ACCEPTED, Instant.parse("2026-10-21T12:00:00Z"));
        when(codec.encode(event)).thenReturn("{\"eventId\":\"one\"}");

        publisher.publish(event);

        var record = sentRecord();
        assertThat(record.key()).isEqualTo("72a11fa8-51ec-4496-9f63-6b1a94f78b73");
        assertThat(record.value()).isEqualTo("{\"eventId\":\"one\"}");
    }

    @Test
    void failedBrokerAcknowledgmentDoesNotReturnReceipt() {
        var failed = new CompletableFuture<SendResult<String, String>>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publishRaw("{}"))
                .isInstanceOf(PublishFailedException.class);
    }

    private void givenAcknowledgment() {
        var sendResult = mock(SendResult.class);
        var metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn("fund-orders");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(42L);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sentRecord() {
        var captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }
}
