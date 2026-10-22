package com.example.fundorders.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaTopicConfiguration {
    @Bean
    public NewTopic sourceTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions()).replicas(properties.replicationFactor()).build();
    }

    @Bean
    public NewTopic deadLetterTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.dlqTopic())
                .partitions(properties.partitions()).replicas(properties.replicationFactor()).build();
    }
}
