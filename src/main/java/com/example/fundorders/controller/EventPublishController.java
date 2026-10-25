package com.example.fundorders.controller;

import com.example.fundorders.exception.ApiExceptionHandler.ApiError;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.service.EventPublisher;
import com.example.fundorders.service.FundOrderEventCodec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fund-order-events")
@Tag(name = "Fund order events")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Basic authentication required", content = @Content),
        @ApiResponse(responseCode = "503", description = "Kafka did not acknowledge publication",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
})
public class EventPublishController {

    private final EventPublisher publisher;
    private final FundOrderEventCodec codec;

    public EventPublishController(EventPublisher publisher, FundOrderEventCodec codec) {
        this.publisher = publisher;
        this.codec = codec;
    }

    @Operation(operationId = "publishEvent", summary = "Validate and publish a typed event",
            description = "Returns 202 after Kafka acknowledges publication. Consumption happens later.")
    @ApiResponse(responseCode = "202", description = "Kafka acknowledged the event",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PublishReceipt.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublishReceipt> publish(@RequestBody FundOrderStatusEvent event) {
        codec.validate(event);
        return ResponseEntity.accepted().body(publisher.publish(event));
    }

    @Operation(operationId = "publishRawEvent", summary = "Publish raw text for consumer validation",
            description = "Sends text unchanged, including malformed JSON. Permanent content defects reach the DLQ during consumption. Kafka producer and broker size limits apply.")
    @ApiResponse(responseCode = "202", description = "Kafka acknowledged the raw text",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PublishReceipt.class)))
    @PostMapping(path = "/raw", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<PublishReceipt> publishRaw(@RequestBody String raw) {
        return ResponseEntity.accepted().body(publisher.publishRaw(raw));
    }
}
