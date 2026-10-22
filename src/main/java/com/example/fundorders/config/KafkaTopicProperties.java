package com.example.fundorders.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicProperties(
        @NotBlank String topic,
        @NotBlank String dlqTopic,
        @Min(1) int partitions,
        @Min(1) short replicationFactor) {
}
