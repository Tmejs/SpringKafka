package com.example.fundorders.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.support.MessagingIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;

class EventPublishControllerIT extends MessagingIntegrationTestSupport {
    private static final String API = "/api/v1/fund-order-events";
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
            "messaging-it-user:messaging-it-password".getBytes(StandardCharsets.UTF_8));
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Test
    void typedRequestPublishesTheEventAndOrderKeyToKafka() throws Exception {
        var orderId = UUID.randomUUID();
        var body = eventJson(UUID.randomUUID(), orderId, "REJECTED");
        var receipt = post(API, "application/json", body);

        assertThat(receipt.topic()).isEqualTo(SOURCE);
        assertThat(receipt.key()).isEqualTo(orderId.toString());
        try (var consumer = recordConsumer(SOURCE, receipt.partition(), receipt.offset())) {
            var record = awaitRecord(consumer, item -> item.offset() == receipt.offset());
            assertThat(record.topic()).isEqualTo(receipt.topic());
            assertThat(record.partition()).isEqualTo(receipt.partition());
            assertThat(record.offset()).isEqualTo(receipt.offset());
            assertThat(record.key()).isEqualTo(orderId.toString());
            assertThat(json.readTree(record.value())).isEqualTo(json.readTree(body));
        }
    }

    @ParameterizedTest(name = "raw request preserves {0} in Kafka")
    @MethodSource("rawMessages")
    void rawRequestPublishesTextUnchanged(String description, String body) throws Exception {
        var receipt = post(API + "/raw", "text/plain; charset=UTF-8", body);

        assertThat(receipt.topic()).isEqualTo(SOURCE);
        assertThat(receipt.key()).isNotBlank();
        try (var consumer = recordConsumer(SOURCE, receipt.partition(), receipt.offset())) {
            var record = awaitRecord(consumer, item -> item.offset() == receipt.offset());
            assertThat(record.topic()).isEqualTo(receipt.topic());
            assertThat(record.partition()).isEqualTo(receipt.partition());
            assertThat(record.offset()).isEqualTo(receipt.offset());
            assertThat(record.key()).isEqualTo(receipt.key());
            assertThat(record.value()).isEqualTo(body);
        }
    }

    private static Stream<Arguments> rawMessages() {
        return Stream.of(
                Arguments.of("JSON with whitespace", " \n" + eventJson(UUID.randomUUID(),
                        UUID.randomUUID(), "ACCEPTED") + "\n "),
                Arguments.of("malformed Unicode text", "not-json-" + UUID.randomUUID() + "-é漢字"));
    }

    private PublishReceipt post(String path, String contentType, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(WAIT)
                .header("Authorization", AUTHORIZATION)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
        var receipt = json.readValue(response.body(), PublishReceipt.class);
        assertThat(receipt.partition()).isBetween(0, 1);
        assertThat(receipt.offset()).isNotNegative();
        return receipt;
    }
}
