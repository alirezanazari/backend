package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.common.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InternalTransferRequest(
        @NotBlank @Size(max = 120) String referenceId,
        @NotBlank @Pattern(regexp = "\\d{18}") String sourceExternalAccountNumber,
        @NotBlank @Pattern(regexp = "\\d{18}") String destinationExternalAccountNumber,
        @NotNull @Valid Money amount,
        @NotBlank @Size(max = 120) String idempotencyKey
) {
}
