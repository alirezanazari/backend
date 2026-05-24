package com.redbank.contracts.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;

public record CardSummary(
        @NotBlank String cardId,
        @NotBlank @Pattern(regexp = "\\d{18}") String accountNumber,
        @NotBlank String maskedPan,
        @NotNull CardStatus status,
        YearMonth expiresAt
) {
}
