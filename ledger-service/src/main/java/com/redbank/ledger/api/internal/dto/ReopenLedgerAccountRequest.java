package com.redbank.ledger.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReopenLedgerAccountRequest(
        @NotBlank
        @Pattern(regexp = "\\d{18}")
        String externalAccountNumber
) {
}
