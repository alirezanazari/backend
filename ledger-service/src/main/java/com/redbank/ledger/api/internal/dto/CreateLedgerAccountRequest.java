package com.redbank.ledger.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLedgerAccountRequest(
        @NotBlank @Pattern(regexp = "\\d{18}") String externalAccountNumber,
        @NotBlank String ownerUserId,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
