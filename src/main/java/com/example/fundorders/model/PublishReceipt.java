package com.example.fundorders.model;

public record PublishReceipt(String topic, int partition, long offset, String key) {
}
