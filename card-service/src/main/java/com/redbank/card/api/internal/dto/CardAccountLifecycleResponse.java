package com.redbank.card.api.internal.dto;

public record CardAccountLifecycleResponse(
        String accountNumber,
        String status,
        Integer updatedCards
) {
}
