package com.example.fundorders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.example.fundorders.exception.InvalidEventException;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.OrderStatus;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FundOrderEventCodecTest {
    private static final String VALID_JSON = """
            {"schemaVersion":1,"eventId":"3c6fc787-e729-46c0-b199-8c8a737c2251",
             "orderId":"72a11fa8-51ec-4496-9f63-6b1a94f78b73","partnerCode":"AGENCY_A",
             "status":"ACCEPTED","occurredAt":"2026-10-21T12:00:00Z"}
            """;

    private final FundOrderEventCodec codec = new FundOrderEventCodec(
            JsonMapper.builder().findAndAddModules().build(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void decodesValidEvent() {
        var event = codec.decode(VALID_JSON);

        assertThat(event).isEqualTo(new FundOrderStatusEvent(1,
                UUID.fromString("3c6fc787-e729-46c0-b199-8c8a737c2251"),
                UUID.fromString("72a11fa8-51ec-4496-9f63-6b1a94f78b73"),
                "AGENCY_A", OrderStatus.ACCEPTED,
                Instant.parse("2026-10-21T12:00:00Z")));
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> codec.decode("{not-json"))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> codec.decode(VALID_JSON.replace("ACCEPTED", "UNKNOWN")))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsMissingEventId() {
        assertThatThrownBy(() -> codec.decode(VALID_JSON.replace(
                "\"eventId\":\"3c6fc787-e729-46c0-b199-8c8a737c2251\",", "")))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsInvalidPartnerCodeFormat() {
        assertThatThrownBy(() -> codec.decode(VALID_JSON.replace("AGENCY_A", "bad")))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertThatThrownBy(() -> codec.decode(VALID_JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void encodedEventRoundTrips() {
        var event = codec.decode(VALID_JSON);
        assertThat(codec.decode(codec.encode(event))).isEqualTo(event);
    }
}
