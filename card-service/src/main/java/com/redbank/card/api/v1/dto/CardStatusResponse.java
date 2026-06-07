package com.redbank.card.api.v1.dto;

import com.redbank.contracts.card.CardStatus;
import java.time.Instant;

public record CardStatusResponse(
        String cardId,
        CardStatus status,
        Instant updatedAt
) {
}
