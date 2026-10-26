package com.example.fundorders.config;

import java.time.Duration;

import com.example.fundorders.exception.InvalidEventException;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaBatchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaBatchConfiguration.class);

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaBatchListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler batchErrorHandler,
            @Value("${app.kafka.concurrency:1}") int concurrency) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(batchErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }

    @Bean
    public DefaultErrorHandler batchErrorHandler(
            KafkaTemplate<String, String> template,
            @Value("${app.kafka.dlq-topic}") String dlqTopic,
            @Value("${app.kafka.retry-delay:1s}") Duration retryDelay,
            @Value("${app.kafka.dlq-send-timeout:10s}") Duration dlqSendTimeout) {
        var recoverer = deadLetterRecoverer(template, dlqTopic, dlqSendTimeout);
        var handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(retryDelay.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    public DeadLetterPublishingRecoverer deadLetterRecoverer(
            KafkaTemplate<String, String> template, String dlqTopic, Duration sendTimeout) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(dlqTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(sendTimeout);
        return recoverer;
    }

}
