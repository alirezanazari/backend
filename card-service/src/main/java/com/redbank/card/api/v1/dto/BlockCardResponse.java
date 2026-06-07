package com.redbank.card.api.v1.dto;

import com.redbank.contracts.card.CardStatus;
import java.time.Instant;

public record BlockCardResponse(
        String cardId,
        CardStatus status,
        Boolean reorderRequired,
        Instant updatedAt
) {
}
