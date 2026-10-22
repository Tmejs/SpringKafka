package com.example.fundorders.event;

import java.util.stream.Collectors;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FundOrderEventCodec {
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
    }
}
