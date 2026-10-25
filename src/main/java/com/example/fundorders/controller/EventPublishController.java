package com.example.fundorders.controller;

import java.nio.charset.StandardCharsets;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.service.EventPublisher;
import com.example.fundorders.service.FundOrderEventCodec;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fund-order-events")
public class EventPublishController {
    private static final int MAX_RAW_BYTES = 64 * 1024;

    private final EventPublisher publisher;
    private final FundOrderEventCodec codec;

    public EventPublishController(EventPublisher publisher, FundOrderEventCodec codec) {
        this.publisher = publisher;
        this.codec = codec;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublishReceipt> publish(@RequestBody FundOrderStatusEvent event) {
        codec.validate(event);
        return ResponseEntity.accepted().body(publisher.publish(event));
    }

    @PostMapping(path = "/raw", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<PublishReceipt> publishRaw(@RequestBody String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_RAW_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Raw message exceeds 64 KiB");
        }
        return ResponseEntity.accepted().body(publisher.publishRaw(raw));
    }
}
