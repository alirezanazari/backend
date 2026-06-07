package com.redbank.card.api.v1.dto;

import com.redbank.contracts.card.CardStatus;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ListCardsRequest(
        List<CardStatus> statuses,
        @Pattern(regexp = "\\d{18}") String accountNumber
) {
}
