package com.example.fundorders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.support.MessagingIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class EventPublishControllerIT extends MessagingIntegrationTestSupport {
    private static final String API = "/api/v1/fund-order-events";
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Test
    void typedRequestPublishesTheEventAndOrderKeyToKafka() throws Exception {
        var orderId = UUID.randomUUID();
        var body = eventJson(UUID.randomUUID(), orderId, "REJECTED");
        var receipt = publish(API, "application/json", body);

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
        var receipt = publish(API + "/raw", "text/plain; charset=UTF-8", body);

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

    private PublishReceipt publish(String path, String contentType, String body) throws Exception {
        var response = mvc.perform(post(path)
                        .with(httpBasic("messaging-it-user", "messaging-it-password"))
                        .contentType(contentType)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse();
        var receipt = json.readValue(response.getContentAsString(StandardCharsets.UTF_8), PublishReceipt.class);
        assertThat(receipt.partition()).isBetween(0, 1);
        assertThat(receipt.offset()).isNotNegative();
        return receipt;
    }
}
