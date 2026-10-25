package com.example.fundorders.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"topic", "partition", "offset", "key"})
public record PublishReceipt(String topic, int partition, long offset, String key) {
}
