package com.example.fundorders.consume;

import java.util.List;

import com.example.fundorders.event.FundOrderEventCodec;
import com.example.fundorders.store.ProcessedEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

@Component
public class FundOrderBatchListener {
    private final FundOrderEventCodec codec;
    private final ProcessedEventService service;

    public FundOrderBatchListener(FundOrderEventCodec codec, ProcessedEventService service) {
        this.codec = codec;
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.topic}", containerFactory = "kafkaBatchListenerContainerFactory")
    public void onMessages(List<ConsumerRecord<String, String>> records) {
        for (var record : records) {
            try {
                service.process(codec.decode(record.value()));
            } catch (Exception failure) {
                throw new BatchListenerFailedException("Fund order event processing failed",
                        failure, record);
            }
        }
    }
}
