package com.example.fundorders.controller;

import java.util.UUID;

import com.example.fundorders.exception.ApiExceptionHandler.ApiError;
import com.example.fundorders.model.EventPage;
import com.example.fundorders.model.ProcessedFundOrderEvent;
import com.example.fundorders.repository.ProcessedEventRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fund-order-events")
@Tag(name = "Fund order events")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Basic authentication required", content = @Content)
})
public class FundOrderQueryController {
    private final ProcessedEventRepository repository;

    public FundOrderQueryController(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Operation(operationId = "listConsumedEvents", summary = "List consumed events",
            description = "Ordered by occurredAt descending, then eventId descending.")
    @ApiResponse(responseCode = "200", description = "Consumed event page",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EventPage.class)))
    @GetMapping
    public EventPage list(
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be nonnegative and size must be between 1 and 100");
        }
        var result = repository.findPage(PageRequest.of(page, size));
        return new EventPage(result.getContent(), page, size, result.getTotalElements());
    }

    @Operation(operationId = "getConsumedEvent", summary = "Get one consumed event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Persisted event",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProcessedFundOrderEvent.class))),
            @ApiResponse(responseCode = "404", description = "Event does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{eventId}")
    public ProcessedFundOrderEvent get(@PathVariable UUID eventId) {
        return repository.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event was not found"));
    }
}
