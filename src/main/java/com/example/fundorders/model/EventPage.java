package com.example.fundorders.model;

import java.util.List;

public record EventPage(List<ProcessedFundOrderEvent> content, int page, int size,
        long totalElements) {
}
