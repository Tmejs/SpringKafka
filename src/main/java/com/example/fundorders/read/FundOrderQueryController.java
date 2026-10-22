package com.example.fundorders.read;

import java.util.List;
import java.util.UUID;

import com.example.fundorders.store.ProcessedEventRepository;
import com.example.fundorders.store.ProcessedFundOrderEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fund-order-events")
public class FundOrderQueryController {
    private final ProcessedEventRepository repository;

    public FundOrderQueryController(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public EventPage list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be nonnegative and size must be between 1 and 100");
        }
        var result = repository.findPage(PageRequest.of(page, size));
        return new EventPage(result.getContent(), page, size, result.getTotalElements());
    }

    @GetMapping("/{eventId}")
    public ProcessedFundOrderEvent get(@PathVariable UUID eventId) {
        return repository.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event was not found"));
    }

    public record EventPage(List<ProcessedFundOrderEvent> content, int page, int size,
            long totalElements) {
    }
}
