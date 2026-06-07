package com.redbank.card.api.v1.dto;

import com.redbank.contracts.card.CardSummary;

public record OrderCardResponse(
        String orderId,
        CardSummary card,
        String orderStatus
) {
}
