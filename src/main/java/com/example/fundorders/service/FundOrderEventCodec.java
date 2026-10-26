package com.example.fundorders.service;

import java.time.Instant;
import java.util.stream.Collectors;

import com.example.fundorders.exception.InvalidEventException;
import com.example.fundorders.model.FundOrderStatusEvent;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FundOrderEventCodec {
    private static final Instant MIN_OCCURRED_AT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_OCCURRED_AT = Instant.parse("9999-12-31T23:59:59.999999Z");
    private final JsonMapper mapper;
    private final Validator validator;

    public FundOrderEventCodec(JsonMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public FundOrderStatusEvent decode(String json) {
        final FundOrderStatusEvent event;
        try {
            event = mapper.readValue(json, FundOrderStatusEvent.class);
        } catch (Exception ex) {
            throw new InvalidEventException("Malformed or incompatible event JSON", ex);
        }
        validate(event);
        return event;
    }

    public String encode(FundOrderStatusEvent event) {
        validate(event);
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize a valid event", ex);
        }
    }

    public void validate(FundOrderStatusEvent event) {
        if (event == null) {
            throw new InvalidEventException("Event is required");
        }
        if (event.schemaVersion() != 1) {
            throw new InvalidEventException("Unsupported event schema version");
        }
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            var fields = violations.stream().map(v -> v.getPropertyPath().toString())
                    .sorted().collect(Collectors.joining(", "));
            throw new InvalidEventException("Invalid event fields: " + fields);
        }
        if (event.occurredAt().isBefore(MIN_OCCURRED_AT) || event.occurredAt().isAfter(MAX_OCCURRED_AT)) {
            throw new InvalidEventException("occurredAt must be between " + MIN_OCCURRED_AT + " and " + MAX_OCCURRED_AT);
        }
    }
}
