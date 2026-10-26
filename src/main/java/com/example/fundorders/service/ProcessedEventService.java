package com.example.fundorders.service;

import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.repository.ProcessedEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedEventService {
    private final ProcessedEventRepository repository;
    private final FundOrderEventCodec codec;

    public ProcessedEventService(ProcessedEventRepository repository, FundOrderEventCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional
    public void process(FundOrderStatusEvent event) {
        codec.validate(event);
        repository.insertIfAbsent(event);
    }
}
