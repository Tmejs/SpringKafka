package com.example.fundorders.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.example.fundorders.FundOrdersApplication;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = FundOrdersApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.security.username=messaging-it-user",
                "app.security.password=messaging-it-password",
                "app.kafka.topic=messaging-boundary-it",
                "app.kafka.dlq-topic=messaging-boundary-it.DLQ",
                "app.kafka.partitions=2",
                "app.kafka.replication-factor=1",
                "spring.kafka.admin.auto-create=true",
                "spring.kafka.consumer.group-id=messaging-boundary-it-group"
        })
@AutoConfigureMockMvc
@ActiveProfiles("default")
@Import(MessagingIntegrationTestSupport.Containers.class)
public abstract class MessagingIntegrationTestSupport {
    protected static final String SOURCE = "messaging-boundary-it";
    protected static final String DLQ = SOURCE + ".DLQ";
    protected static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private KafkaContainer kafka;

    protected KafkaProducer<String, String> directProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"));
    }

    protected KafkaConsumer<String, String> recordConsumer(String topic, int partition, long offset) {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "assert-record-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var assignment = new TopicPartition(topic, partition);
        consumer.assign(List.of(assignment));
        consumer.seek(assignment, offset);
        return consumer;
    }

    protected ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer,
            Predicate<ConsumerRecord<String, String>> matches) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(200))) {
                if (matches.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("Expected Kafka record not observed on " + consumer.assignment());
    }

    protected long endOffset(String topic, int partition) throws Exception {
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()))) {
            var assignment = new TopicPartition(topic, partition);
            return admin.listOffsets(Map.of(assignment, OffsetSpec.latest())).all()
                    .get(10, TimeUnit.SECONDS).get(assignment).offset();
        }
    }

    protected static String eventJson(UUID eventId, UUID orderId, String status) {
        return """
                {"schemaVersion":1,"eventId":"%s","orderId":"%s",
                 "partnerCode":"AGENCY_A","status":"%s","occurredAt":"2026-10-26T12:00:00Z"}
                """.formatted(eventId, orderId, status);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.0.0");
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:16-alpine");
        }
    }
}
