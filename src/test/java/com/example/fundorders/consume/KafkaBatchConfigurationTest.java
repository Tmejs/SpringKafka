package com.example.fundorders.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

import com.example.fundorders.event.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
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

    @Test
    void storageFailureSeeksToFailedOffsetWithoutPublishingOrCommittingIt() {
        @SuppressWarnings("unchecked")
        var template = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        var handler = configuration.batchErrorHandler(template, "fund-orders.DLQ",
                Duration.ofMillis(1), Duration.ofSeconds(2));
        var record = new ConsumerRecord<>("fund-orders", 0, 7L, "key", "valid");
        var topicPartition = new TopicPartition("fund-orders", 0);
        var batch = new ConsumerRecords<>(Map.of(topicPartition, List.of(record)));
        @SuppressWarnings("unchecked")
        var consumer = (Consumer<String, String>) mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var failure = new BatchListenerFailedException("database unavailable",
                new IllegalStateException("database unavailable"), record);

        assertThatThrownBy(() -> handler.handleBatch(failure, batch, consumer, container,
                () -> {})).hasMessageContaining("Record in retry");

        verify(consumer).seek(topicPartition, 7L);
        verify(template, never()).send(any(ProducerRecord.class));
        assertThat(mockingDetails(consumer).getInvocations())
                .noneMatch(call -> call.getMethod().getName().equals("commitSync"));
    }

    @Test
    void failedDlqSendSeeksToSourceOffsetWithoutCommittingIt() {
        @SuppressWarnings("unchecked")
        var template = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        var failedSend = new CompletableFuture<org.springframework.kafka.support.SendResult<String, String>>();
        failedSend.completeExceptionally(new IllegalStateException("DLQ unavailable"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failedSend);
        var handler = configuration.batchErrorHandler(template, "fund-orders.DLQ",
                Duration.ofMillis(1), Duration.ofSeconds(2));
        var record = new ConsumerRecord<>("fund-orders", 0, 7L, "key", "bad");
        var topicPartition = new TopicPartition("fund-orders", 0);
        var batch = new ConsumerRecords<>(Map.of(topicPartition, List.of(record)));
        @SuppressWarnings("unchecked")
        var consumer = (Consumer<String, String>) mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var failure = new BatchListenerFailedException("invalid",
                new InvalidEventException("bad value"), record);

        assertThatThrownBy(() -> handler.handleBatch(failure, batch, consumer, container,
                () -> {})).hasMessageContaining("Record in retry");

        verify(consumer).seek(topicPartition, 7L);
        assertThat(mockingDetails(consumer).getInvocations())
                .noneMatch(call -> call.getMethod().getName().equals("commitSync"));
    }
}
