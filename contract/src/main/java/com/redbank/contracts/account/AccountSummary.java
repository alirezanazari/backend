package com.redbank.contracts.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountSummary(
        @NotBlank String profileId,
        @NotBlank @Pattern(regexp = "\\d{18}") String accountNumber,
        @NotBlank String iban,
        @NotNull AccountType type,
        @NotNull AccountStatus status,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Valid BalanceSummary balance
) {
}
