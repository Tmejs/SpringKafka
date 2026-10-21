package com.example.fundorders.publish;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.example.fundorders.event.FundOrderEventCodec;
import com.example.fundorders.event.FundOrderStatusEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final KafkaTemplate<String, String> template;
    private final FundOrderEventCodec codec;
    private final String topic;
    private final Duration sendTimeout;

    public EventPublisher(KafkaTemplate<String, String> template, FundOrderEventCodec codec,
            @Value("${app.kafka.topic}") String topic,
            @Value("${app.kafka.send-timeout:5s}") Duration sendTimeout) {
        this.template = template;
        this.codec = codec;
        this.topic = topic;
        this.sendTimeout = sendTimeout;
    }

    public PublishReceipt publish(FundOrderStatusEvent event) {
        return send(event.orderId().toString(), codec.encode(event));
    }

    public PublishReceipt publishRaw(String raw) {
        return send(UUID.randomUUID().toString(), raw);
    }

    private PublishReceipt send(String key, String value) {
        try {
            var record = new ProducerRecord<>(topic, key, value);
            var metadata = template.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .getRecordMetadata();
            return new PublishReceipt(metadata.topic(), metadata.partition(), metadata.offset(), key);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Kafka publication interrupted", ex);
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            throw new PublishFailedException("Kafka did not acknowledge publication", ex);
        }
    }
}
