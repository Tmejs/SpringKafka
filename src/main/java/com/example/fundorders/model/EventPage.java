package com.example.fundorders.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"content", "page", "size", "totalElements"})
public record EventPage(List<ProcessedFundOrderEvent> content, int page, int size,
        long totalElements) {
}
