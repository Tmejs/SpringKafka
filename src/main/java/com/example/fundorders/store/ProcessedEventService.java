package com.example.fundorders.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.example.fundorders.event.FundOrderEventCodec;
import com.example.fundorders.event.FundOrderStatusEvent;
import com.example.fundorders.event.InvalidEventException;
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
        var hash = hash(codec.encode(event));
        if (repository.insertIfAbsent(event, hash)) {
            return;
        }
        var existingHash = repository.findPayloadHash(event.eventId())
                .orElseThrow(() -> new IllegalStateException("Conflicting event row disappeared"));
        if (!existingHash.equals(hash)) {
            throw new InvalidEventException("Event ID was reused with different content");
        }
    }

    private static String hash(String canonicalJson) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
