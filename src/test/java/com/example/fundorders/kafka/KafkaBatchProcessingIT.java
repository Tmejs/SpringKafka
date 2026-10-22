package com.example.fundorders.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.repository.ProcessedEventRepository;
import com.example.fundorders.service.FundOrderEventCodec;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(KafkaBatchProcessingIT.ObservationConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaBatchProcessingIT {
    private static final String SOURCE = "batch-processing-it";
    private static final String DLQ = SOURCE + ".DLQ";
    private static final String GROUP = "batch-processing-it-group";
    private static final TopicPartition SOURCE_PARTITION = new TopicPartition(SOURCE, 0);
    private static final TopicPartition DLQ_PARTITION = new TopicPartition(DLQ, 0);
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("app.security.username", () -> "analyst");
        properties.add("app.security.password", () -> "batch-test-secret");
        properties.add("app.kafka.topic", () -> SOURCE);
        properties.add("app.kafka.dlq-topic", () -> DLQ);
        properties.add("app.kafka.partitions", () -> 1);
        properties.add("app.kafka.concurrency", () -> 1);
        properties.add("spring.kafka.consumer.group-id", () -> GROUP);
        properties.add("spring.kafka.consumer.properties.max.poll.records", () -> 3);
    }

    @Autowired
    KafkaListenerEndpointRegistry registry;
    @Autowired
    ProcessedEventRepository repository;
    @Autowired
    FundOrderEventCodec codec;
    @Autowired
    BatchProbe probe;
    @Autowired
    KafkaTemplate<String, String> template;
    @MockitoSpyBean(name = "kafkaTemplate")
    KafkaTemplate<?, ?> observedTemplate;

    private final AtomicReference<DlqFailureWindow> failureWindow = new AtomicReference<>();
    private MessageListenerContainer listener;
    private Admin admin;
    private long initialRows;
    private long initialDlqOffset;

    @BeforeEach
    void prepareControlledListener() throws Exception {
        assertThat(registry.getListenerContainers()).hasSize(1);
        listener = registry.getListenerContainers().iterator().next();
        assertThat(listener.isRunning()).isFalse();
        assertThat(template).isSameAs(observedTemplate);
        probe.deliveries.clear();
        admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()));
        initialRows = repository.count();
        initialDlqOffset = endOffset(DLQ_PARTITION);

        doAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            var window = failureWindow.get();
            if (DLQ.equals(record.topic()) && window != null) {
                if (window.attempts.incrementAndGet() == 1) {
                    return CompletableFuture.<SendResult<String, String>>failedFuture(
                            new IllegalStateException("Injected DLQ publication failure"));
                }
                window.retryEntered.countDown();
                if (!window.allowPublication.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("DLQ recovery gate was not released");
                }
            }
            return invocation.callRealMethod();
        }).when(template).send(any(ProducerRecord.class));
    }

    @AfterEach
    void stopListenerAndReleaseRecovery() {
        var window = failureWindow.get();
        if (window != null) {
            window.allowPublication.countDown();
        }
        if (listener != null) {
            listener.stop();
        }
        if (admin != null) {
            admin.close(Duration.ofSeconds(5));
        }
    }

    @Test
    void deliversOneRealBatchAndRecoversOnlyItsInvalidRecord() throws Exception {
        var batch = publishBatch();

        listener.start();

        assertInitialBatchWasDelivered(batch);
        assertProcessingCompleted(batch);
        assertDeadLetter(batch);
    }

    @Test
    void failedDlqPublicationKeepsTheFailedRecordAndTailPendingUntilRecovery() throws Exception {
        var window = new DlqFailureWindow();
        failureWindow.set(window);
        var batch = publishBatch();

        try {
            listener.start();
            assertInitialBatchWasDelivered(batch);
            await().alias("failed DLQ publication causes redelivery")
                    .atMost(WAIT).until(() -> window.retryEntered.getCount() == 0);

            assertThat(window.attempts.get()).isEqualTo(2);
            assertThat(probe.deliveries).contains(batch.offsets().subList(1, 3));
            assertThat(repository.findByEventId(batch.firstId())).isPresent();
            assertThat(repository.findByEventId(batch.lastId())).isEmpty();
            assertThat(repository.count()).isEqualTo(initialRows + 1);
            assertThat(committedOffset()).isEqualTo(batch.offsets().get(1));
            assertThat(endOffset(DLQ_PARTITION)).isEqualTo(initialDlqOffset);
        } finally {
            window.allowPublication.countDown();
        }

        assertProcessingCompleted(batch);
        assertDeadLetter(batch);
    }

    private PublishedBatch publishBatch() throws Exception {
        var firstId = UUID.randomUUID();
        var lastId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var malformed = "not-json-" + UUID.randomUUID();
        var offsets = List.of(
                publish(orderId, event(firstId, orderId, OrderStatus.ACCEPTED)),
                publish(orderId, malformed),
                publish(orderId, event(lastId, orderId, OrderStatus.EXECUTED)));
        return new PublishedBatch(firstId, lastId, malformed, offsets);
    }

    private String event(UUID eventId, UUID orderId, OrderStatus status) {
        return codec.encode(new FundOrderStatusEvent(1, eventId, orderId,
                "AGENCY_A", "LU1234567890", status, Instant.parse("2026-10-21T12:00:00Z")));
    }

    private long publish(UUID orderId, String value) throws Exception {
        return template.send(SOURCE, 0, orderId.toString(), value)
                .get(10, TimeUnit.SECONDS).getRecordMetadata().offset();
    }

    private void assertInitialBatchWasDelivered(PublishedBatch batch) {
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(probe.deliveries)
                        .as("one real delivery contains A, B, and C")
                        .contains(batch.offsets()));
    }

    private void assertProcessingCompleted(PublishedBatch batch) {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(repository.findByEventId(batch.firstId())).isPresent();
            assertThat(repository.findByEventId(batch.lastId())).isPresent();
            assertThat(repository.count()).isEqualTo(initialRows + 2);
            assertThat(committedOffset()).isEqualTo(batch.offsets().get(2) + 1);
        });
    }

    private long committedOffset() throws Exception {
        var offsets = admin.listConsumerGroupOffsets(GROUP)
                .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
        var offset = offsets.get(SOURCE_PARTITION);
        return offset == null ? -1 : offset.offset();
    }

    private long endOffset(TopicPartition partition) throws Exception {
        return admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all()
                .get(10, TimeUnit.SECONDS).get(partition).offset();
    }

    private void assertDeadLetter(PublishedBatch batch) throws Exception {
        assertThat(endOffset(DLQ_PARTITION)).isEqualTo(initialDlqOffset + 1);
        var properties = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "batch-dlq-assert-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.assign(List.of(DLQ_PARTITION));
            consumer.seek(DLQ_PARTITION, initialDlqOffset);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            await().atMost(WAIT).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(100)).forEach(records::add);
                assertThat(records).hasSize(1);
            });
            var deadLetter = records.getFirst();
            assertThat(deadLetter.value()).isEqualTo(batch.malformed());
            var headers = deadLetter.headers();
            assertThat(new String(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    StandardCharsets.UTF_8)).isEqualTo(SOURCE);
            assertThat(ByteBuffer.wrap(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)
                    .value()).getInt()).isZero();
            assertThat(ByteBuffer.wrap(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)
                    .value()).getLong()).isEqualTo(batch.offsets().get(1));
            assertThat(headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        }
    }

    private record PublishedBatch(UUID firstId, UUID lastId, String malformed, List<Long> offsets) {
    }

    private static final class DlqFailureWindow {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch retryEntered = new CountDownLatch(1);
        private final CountDownLatch allowPublication = new CountDownLatch(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfiguration {
        @Bean
        static BatchProbe batchProbe() {
            return new BatchProbe();
        }
    }

    static final class BatchProbe implements BeanPostProcessor, BatchInterceptor<String, String> {
        private final ConcurrentLinkedQueue<List<Long>> deliveries = new ConcurrentLinkedQueue<>();

        @Override
        @SuppressWarnings("unchecked")
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if ("kafkaBatchListenerContainerFactory".equals(beanName)) {
                var factory = (ConcurrentKafkaListenerContainerFactory<String, String>) bean;
                factory.setAutoStartup(false);
                factory.setBatchInterceptor(this);
            }
            return bean;
        }

        @Override
        public ConsumerRecords<String, String> intercept(ConsumerRecords<String, String> records,
                Consumer<String, String> consumer) {
            deliveries.add(StreamSupport.stream(records.spliterator(), false)
                    .map(ConsumerRecord::offset).toList());
            return records;
        }
    }
}
