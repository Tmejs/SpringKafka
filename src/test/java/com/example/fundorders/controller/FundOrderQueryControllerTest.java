package com.example.fundorders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.fundorders.exception.ApiExceptionHandler;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.model.ProcessedFundOrderEvent;
import com.example.fundorders.repository.ProcessedEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FundOrderQueryControllerTest {
    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FundOrderQueryController(repository))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsStablePaginatedView() throws Exception {
        var event = storedEvent();
        when(repository.findPage(any())).thenReturn(new PageImpl<>(List.of(event)));

        mvc.perform(get("/api/v1/fund-order-events?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(event.eventId().toString()))
                .andExpect(jsonPath("$.content[0].fundIsin").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        verify(repository).findPage(PageRequest.of(0, 20));
    }

    @Test
    void rejectsOutOfRangePageSize() throws Exception {
        mvc.perform(get("/api/v1/fund-order-events?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/fund-order-events?page=-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fund-order-events?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/fund-order-events/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsOneEventOrNotFound() throws Exception {
        var event = storedEvent();
        when(repository.findByEventId(event.eventId())).thenReturn(Optional.of(event));

        mvc.perform(get("/api/v1/fund-order-events/" + event.eventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.fundIsin").doesNotExist());
        mvc.perform(get("/api/v1/fund-order-events/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static ProcessedFundOrderEvent storedEvent() {
        return new ProcessedFundOrderEvent(1, UUID.randomUUID(), UUID.randomUUID(),
                "AGENCY_A", OrderStatus.REJECTED,
                Instant.parse("2026-10-21T12:00:00Z"), Instant.parse("2026-10-21T12:00:01Z"));
    }
}
