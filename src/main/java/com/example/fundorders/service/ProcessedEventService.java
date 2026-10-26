package com.example.fundorders.service;

import java.sql.SQLException;

import com.example.fundorders.exception.InvalidEventException;
import com.example.fundorders.model.FundOrderStatusEvent;
import com.example.fundorders.repository.ProcessedEventRepository;

import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            repository.insertIfAbsent(event);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                // PostgreSQL datetime overflow is a permanent payload defect.
                if (cause instanceof SQLException sql && "22008".equals(sql.getSQLState())) {
                    throw new InvalidEventException("Event timestamp cannot be stored", exception);
                }
            }
            throw exception;
        }
    }
}
