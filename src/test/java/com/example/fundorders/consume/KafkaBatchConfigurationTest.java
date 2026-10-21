package com.example.fundorders.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.example.fundorders.event.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.ExceptionMatcher;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaBatchConfigurationTest {
    private final KafkaBatchConfiguration configuration = new KafkaBatchConfiguration();

    @Test
    void factoryUsesBatchDeliveryAndBatchAcknowledgment() {
        var factory = configuration.kafkaBatchListenerContainerFactory(
                mock(ConsumerFactory.class),
                configuration.batchErrorHandler(mock(KafkaTemplate.class), "fund-orders.DLQ",
                        Duration.ofMillis(250), Duration.ofSeconds(2)), 2);

        assertThat(factory.isBatchListener()).isTrue();
        assertThat(factory.getContainerProperties().getAckMode().name()).isEqualTo("BATCH");
    }

    @Test
    void permanentEventFailureSkipsRetriesButInfrastructureFailureRetries() {
        var handler = configuration.batchErrorHandler(mock(KafkaTemplate.class), "fund-orders.DLQ",
                Duration.ofMillis(250), Duration.ofSeconds(2));
        var matcher = (ExceptionMatcher) ReflectionTestUtils.invokeMethod(handler, "getExceptionMatcher");
        var record = new ConsumerRecord<>("fund-orders", 0, 1L, "key", "invalid");

        assertThat(matcher.match(new BatchListenerFailedException("invalid",
                new InvalidEventException("bad value"), record))).isFalse();
        assertThat(matcher.match(new BatchListenerFailedException("outage",
                new IllegalStateException("database unavailable"), record))).isTrue();
    }

    @Test
    void failedDlqPublicationIsNotTreatedAsRecovery() {
        @SuppressWarnings("unchecked")
        var template = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        var failed = new CompletableFuture<org.springframework.kafka.support.SendResult<String, String>>();
        failed.completeExceptionally(new IllegalStateException("DLQ unavailable"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failed);
        var recoverer = configuration.deadLetterRecoverer(template, "fund-orders.DLQ",
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> recoverer.accept(
                new ConsumerRecord<>("fund-orders", 0, 1L, "key", "invalid"),
                new InvalidEventException("bad value")))
                .isInstanceOf(RuntimeException.class);
    }
}
