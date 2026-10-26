package com.example.fundorders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.example.fundorders.exception.ApiExceptionHandler;
import com.example.fundorders.exception.PublishFailedException;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.service.EventPublisher;
import com.example.fundorders.service.FundOrderEventCodec;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class EventPublishControllerTest {
    private EventPublisher publisher;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        publisher = mock(EventPublisher.class);
        mvc = MockMvcBuilders.standaloneSetup(new EventPublishController(publisher,
                        new FundOrderEventCodec(JsonMapper.builder().findAndAddModules().build(),
                                Validation.buildDefaultValidatorFactory().getValidator())))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void typedInvalidBodyReturnsBadRequestBeforePublication() throws Exception {
        mvc.perform(post("/api/v1/fund-order-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\":1}"))
                .andExpect(status().isBadRequest());

        verify(publisher, never()).publish(any(FundOrderStatusEvent.class));
    }

    @Test
    void typedOutOfRangeTimestampIsRejectedBeforePublication() throws Exception {
        mvc.perform(post("/api/v1/fund-order-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schemaVersion":1,"eventId":"3c6fc787-e729-46c0-b199-8c8a737c2251",
                         "orderId":"72a11fa8-51ec-4496-9f63-6b1a94f78b73","partnerCode":"AGENCY_A",
                         "status":"ACCEPTED","occurredAt":"+300000-01-01T00:00:00Z"}
                        """))
                .andExpect(status().isBadRequest());

        verify(publisher, never()).publish(any(FundOrderStatusEvent.class));
    }

    @Test
    void typedValidBodyReturnsAccepted() throws Exception {
        when(publisher.publish(any(FundOrderStatusEvent.class)))
                .thenReturn(new PublishReceipt("fund-orders", 0, 42, "order-key"));

        mvc.perform(post("/api/v1/fund-order-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schemaVersion":1,"eventId":"3c6fc787-e729-46c0-b199-8c8a737c2251",
                         "orderId":"72a11fa8-51ec-4496-9f63-6b1a94f78b73","partnerCode":"AGENCY_A",
                         "status":"ACCEPTED","occurredAt":"2026-10-21T12:00:00Z"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.offset").value(42));
    }

    @Test
    void rawMalformedTextIsAcceptedForKafkaProcessing() throws Exception {
        when(publisher.publishRaw("{not-json"))
                .thenReturn(new PublishReceipt("fund-orders", 0, 43, "raw-key"));

        mvc.perform(post("/api/v1/fund-order-events/raw")
                .contentType(MediaType.TEXT_PLAIN)
                .content("{not-json"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.offset").value(43));
    }

    @Test
    void rawUtf8TextAboveFormerLimitIsPublishedUnchanged() throws Exception {
        var raw = "é".repeat(32769);
        when(publisher.publishRaw(raw))
                .thenReturn(new PublishReceipt("fund-orders", 0, 43, "raw-key"));

        mvc.perform(post("/api/v1/fund-order-events/raw")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .content(raw))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.offset").value(43));

        verify(publisher).publishRaw(raw);
    }

    @Test
    void brokerFailureReturnsServiceUnavailable() throws Exception {
        when(publisher.publishRaw("{}"))
                .thenThrow(new PublishFailedException("Broker did not acknowledge", new RuntimeException()));

        mvc.perform(post("/api/v1/fund-order-events/raw")
                .contentType(MediaType.TEXT_PLAIN)
                .content("{}"))
                .andExpect(status().isServiceUnavailable());
    }
}
