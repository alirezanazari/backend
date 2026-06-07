package com.redbank.card.api.v1.dto;

import com.redbank.contracts.card.CardSummary;
import java.util.List;

public record ListCardsResponse(
        List<CardSummary> cards
) {
}
