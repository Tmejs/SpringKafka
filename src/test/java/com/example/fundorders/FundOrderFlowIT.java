package com.example.fundorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.repository.ProcessedEventRepository;
import com.example.fundorders.support.MessagingIntegrationTestSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class FundOrderFlowIT extends MessagingIntegrationTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    ProcessedEventRepository repository;

    @ParameterizedTest(name = "{0} request is published, consumed and persisted")
    @MethodSource("publicationPaths")
    void publishedEventIsConsumedAndStored(String description, String path, String contentType) throws Exception {
        var eventId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var body = eventJson(eventId, orderId, "ACCEPTED");
        assertThat(repository.findByEventId(eventId)).isEmpty();

        mvc.perform(post(path)
                        .with(httpBasic("messaging-it-user", "messaging-it-password"))
                        .contentType(contentType)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.topic").value(SOURCE));

        await().atMost(WAIT).untilAsserted(() -> {
            var result = repository.findByEventId(eventId);
            assertThat(result).isPresent();
            var stored = result.orElseThrow();
            assertThat(stored.eventId()).isEqualTo(eventId);
            assertThat(stored.schemaVersion()).isEqualTo(1);
            assertThat(stored.orderId()).isEqualTo(orderId);
            assertThat(stored.partnerCode()).isEqualTo("AGENCY_A");
            assertThat(stored.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(stored.occurredAt()).isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
            assertThat(stored.processedAt()).isNotNull();
        });
    }

    private static Stream<Arguments> publicationPaths() {
        return Stream.of(
                Arguments.of("typed", "/api/v1/fund-order-events", "application/json"),
                Arguments.of("raw", "/api/v1/fund-order-events/raw", "text/plain"));
    }
}
