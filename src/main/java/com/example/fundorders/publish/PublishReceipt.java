package com.example.fundorders.publish;

public record PublishReceipt(String topic, int partition, long offset, String key) {
}
