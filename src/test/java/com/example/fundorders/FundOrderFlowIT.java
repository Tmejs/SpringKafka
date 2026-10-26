package com.example.fundorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FundOrderFlowIT {
    private static final String SOURCE_TOPIC = "fund-order-events";
    private static final String DLQ_TOPIC = "fund-order-events.DLQ";
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:integration-secret".getBytes(StandardCharsets.UTF_8));

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
        properties.add("app.security.password", () -> "integration-secret");
        properties.add("app.kafka.partitions", () -> 1);
        properties.add("app.kafka.replication-factor", () -> 1);
        properties.add("spring.kafka.admin.auto-create", () -> true);
        properties.add("spring.kafka.consumer.properties.max.poll.records", () -> 3);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void swaggerUiAndDocumentsArePublicWhileBusinessApiRequiresBasic() throws Exception {
        for (var path : List.of("/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js", "/v3/api-docs",
                "/v3/api-docs/swagger-config", "/v3/api-docs.yaml")) {
            assertThat(send("GET", path, null, null, false).statusCode()).isEqualTo(200);
            assertThat(send("GET", path, null, null, true).statusCode()).isEqualTo(200);
        }
        assertThat(send("GET", "/swagger-ui/index.html", null, null, false).body())
                .contains("Swagger UI");
        assertThat(send("GET", "/swagger-ui.html", null, null, false).statusCode()).isEqualTo(302);
        assertThat(send("GET", "/api/v1/fund-order-events", null, null, false).statusCode()).isEqualTo(401);
    }

    @Test
    void generatedOpenApiDescribesControllerContractAndExportsBuildArtifact() throws Exception {
        var response = send("GET", "/v3/api-docs", null, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
        var document = JsonMapper.builder().build().readTree(response.body());
        assertThat(document.path("openapi").asString()).startsWith("3.");
        assertThat(document.at("/servers/0/url").asString()).isEqualTo("/");
        assertThat(document.path("paths").size()).isEqualTo(3);
        var collection = document.at("/paths/~1api~1v1~1fund-order-events");
        assertThat(collection.at("/post/responses/202").isMissingNode()).isFalse();
        assertThat(collection.at("/post/responses/200").isMissingNode()).isTrue();
        assertThat(collection.at("/post/requestBody/content/application~1json").isMissingNode()).isFalse();
        assertThat(collection.at("/post/responses/202/content/application~1json/schema/$ref").asString())
                .endsWith("/PublishReceipt");
        assertThat(collection.at("/post/responses/503/content/application~1json/schema/$ref").asString())
                .endsWith("/ApiError");
        assertThat(collection.at("/get/responses/200/content/application~1json/schema/$ref").asString())
                .endsWith("/EventPage");
        assertThat(collection.at("/get/parameters").size()).isEqualTo(2);
        var parameterNames = new ArrayList<String>();
        for (var parameter : collection.at("/get/parameters")) {
            var name = parameter.path("name").asString();
            parameterNames.add(name);
            assertThat(parameter.at("/schema/default").isIntegralNumber()).isTrue();
            assertThat(parameter.at("/schema/type").asString()).isEqualTo("integer");
            assertThat(parameter.at("/schema/format").asString()).isEqualTo("int32");
            assertThat(parameter.at("/schema/default").asInt()).isEqualTo(name.equals("page") ? 0 : 20);
            assertThat(parameter.at("/schema/minimum").asInt()).isEqualTo(name.equals("page") ? 0 : 1);
            if (name.equals("size")) {
                assertThat(parameter.at("/schema/maximum").asInt()).isEqualTo(100);
            }
        }
        assertThat(parameterNames).containsExactlyInAnyOrder("page", "size");
        assertThat(document.at("/paths/~1api~1v1~1fund-order-events~1raw/post/requestBody/content/text~1plain")
                .isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1api~1v1~1fund-order-events~1{eventId}/get/responses/404")
                .isMissingNode()).isFalse();
        assertThat(document.at("/components/securitySchemes/basicAuth/scheme").asString()).isEqualTo("basic");
        assertThat(document.at("/security/0/basicAuth").isArray()).isTrue();
        var eventSchema = document.at("/components/schemas/FundOrderStatusEvent");
        assertThat(eventSchema.path("required").toString())
                .contains("eventId", "orderId", "partnerCode", "status", "occurredAt", "schemaVersion");
        assertThat(eventSchema.at("/properties/schemaVersion/enum").toString()).isEqualTo("[1]");
        assertThat(eventSchema.at("/properties/partnerCode/pattern").asString())
                .isEqualTo("^[A-Z0-9_]{2,32}$");
        assertThat(eventSchema.at("/properties/status/enum").toString())
                .contains("ACCEPTED", "REJECTED", "EXECUTED");

        var output = Path.of(System.getProperty("openapi.outputDirectory", "target/openapi"));
        Files.createDirectories(output);
        Files.writeString(output.resolve("openapi.json"), response.body(), StandardCharsets.UTF_8);
    }

    @Test
    void typedPublishBecomesAuthenticatedReadAndHealthIsPublic() throws Exception {
        var eventId = UUID.randomUUID();
        var payload = eventJson(eventId, UUID.randomUUID(), "ACCEPTED");

        assertThat(send("POST", "/api/v1/fund-order-events", "application/json", payload, true).statusCode())
                .isEqualTo(202);
        assertThat(send("POST", "/api/v1/fund-order-events", "application/json",
                eventJson(UUID.randomUUID(), UUID.randomUUID(), "UNKNOWN_STATUS"), true)
                .statusCode()).isEqualTo(400);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(send("GET", "/api/v1/fund-order-events/" + eventId, null, null, true).statusCode())
                        .isEqualTo(200));
        assertThat(read(eventId).body()).doesNotContain("\"fundIsin\"");
        assertThat(send("GET", "/api/v1/fund-order-events", null, null, false).statusCode())
                .isEqualTo(401);
        assertThat(send("GET", "/actuator/health/liveness", null, null, false).statusCode())
                .isEqualTo(200);
        assertThat(send("GET", "/actuator/health/readiness", null, null, false).statusCode())
                .isEqualTo(200);
    }

    @Test
    void rawValidAndRejectedEventsPersistWhileMalformedAndInvalidEventsReachDlq() throws Exception {
        var first = UUID.randomUUID();
        var last = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var malformed = "not-json-" + UUID.randomUUID();
        var invalidId = UUID.randomUUID();
        var invalid = eventJson(invalidId, orderId, "UNKNOWN_STATUS");
        var missingId = UUID.randomUUID();
        var missingEventId = eventJson(missingId, orderId, "ACCEPTED")
                .replace("\"eventId\":\"" + missingId + "\",", "");
        var unsupportedVersion = eventJson(UUID.randomUUID(), orderId, "ACCEPTED")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2");

        assertThat(postRaw(eventJson(first, orderId, "REJECTED")).statusCode()).isEqualTo(202);
        assertThat(postRaw(malformed).statusCode()).isEqualTo(202);
        assertThat(postRaw(invalid).statusCode()).isEqualTo(202);
        assertThat(postRaw(missingEventId).statusCode()).isEqualTo(202);
        assertThat(postRaw(unsupportedVersion).statusCode()).isEqualTo(202);
        assertThat(postRaw(eventJson(last, orderId, "ACCEPTED")).statusCode()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(read(first).statusCode()).isEqualTo(200);
            assertThat(read(first).body()).contains("REJECTED");
            assertThat(read(last).statusCode()).isEqualTo(200);
        });
        assertThat(read(invalidId).statusCode()).isEqualTo(404);
        var deadLetters = deadLettersFor(List.of(malformed, invalid, missingEventId,
                unsupportedVersion));
        assertThat(deadLetters).hasSize(4);
        for (var record : deadLetters) {
            assertThat(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
            assertThat(new String(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    StandardCharsets.UTF_8)).isEqualTo(SOURCE_TOPIC);
            assertThat(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
            assertThat(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        }
    }

    @Test
    void rawBodyAboveFormerLimitReachesDlqUnchanged() throws Exception {
        var raw = "x".repeat(64 * 1024 + 1);
        var response = postRaw(raw);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(deadLettersFor(List.of(raw)))
                .extracting(ConsumerRecord::value)
                .containsExactly(raw);
    }

    @Test
    void rawUtf8TextReachesDlqWithoutCharacterChanges() throws Exception {
        var malformedJson = "é";

        assertThat(postRaw(malformedJson).statusCode()).isEqualTo(202);
        assertThat(deadLettersFor(List.of(malformedJson)))
                .extracting(ConsumerRecord::value)
                .containsExactly(malformedJson);
    }

    @Test
    void repeatedEventIdsPreserveOriginalWithoutDlqAndLaterEventsContinue() throws Exception {
        var eventId = UUID.randomUUID();
        var tailId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var original = eventJson(eventId, orderId, "ACCEPTED");
        var changedReplay = eventJson(eventId, orderId, "REJECTED");
        var dlqPartition = new TopicPartition(DLQ_TOPIC, 0);
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()))) {
            var initialDlqOffset = admin.listOffsets(Map.of(dlqPartition, OffsetSpec.latest()))
                    .all().get(10, TimeUnit.SECONDS).get(dlqPartition).offset();
            assertThat(postRaw(original).statusCode()).isEqualTo(202);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(read(eventId).statusCode()).isEqualTo(200));
            var storedOriginal = read(eventId).body();

            assertThat(postRaw(original).statusCode()).isEqualTo(202);
            assertThat(postRaw(changedReplay).statusCode()).isEqualTo(202);
            assertThat(postRaw(eventJson(tailId, orderId, "EXECUTED")).statusCode()).isEqualTo(202);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(read(tailId).statusCode()).isEqualTo(200));

            assertThat(read(eventId).body()).isEqualTo(storedOriginal);
            assertThat(admin.listOffsets(Map.of(dlqPartition, OffsetSpec.latest()))
                    .all().get(10, TimeUnit.SECONDS).get(dlqPartition).offset())
                    .isEqualTo(initialDlqOffset);
        }
    }

    private HttpResponse<String> postRaw(String payload) throws Exception {
        return send("POST", "/api/v1/fund-order-events/raw", "text/plain", payload, true);
    }

    private HttpResponse<String> read(UUID eventId) throws Exception {
        return send("GET", "/api/v1/fund-order-events/" + eventId, null, null, true);
    }

    private HttpResponse<String> send(String method, String path, String contentType,
            String body, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (authenticated) {
            request.header("Authorization", AUTH);
        }
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<ConsumerRecord<String, String>> deadLettersFor(List<String> expectedValues) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-assert-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(DLQ_TOPIC));
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline && records.size() < expectedValues.size()) {
                for (var record : consumer.poll(Duration.ofMillis(300))) {
                    if (expectedValues.contains(record.value())) {
                        records.add(record);
                    }
                }
            }
            var extraDeadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (System.nanoTime() < extraDeadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (expectedValues.contains(record.value())) {
                        records.add(record);
                    }
                }
            }
        }
        assertThat(records).extracting(ConsumerRecord::value).containsExactlyInAnyOrderElementsOf(expectedValues);
        return records;
    }

    private static String eventJson(UUID eventId, UUID orderId, String status) {
        return """
                {"schemaVersion":1,"eventId":"%s","orderId":"%s",\
                "partnerCode":"AGENCY_A",\
                "status":"%s","occurredAt":"2026-10-21T12:00:00Z"}
                """.formatted(eventId, orderId, status);
    }
}
