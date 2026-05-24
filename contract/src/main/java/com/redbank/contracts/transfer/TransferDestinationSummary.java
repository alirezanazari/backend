package com.redbank.contracts.transfer;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TransferDestinationSummary(
        @NotNull DestinationType destinationType,
        @NotBlank String destinationName,
        @Valid Image image,
        @Pattern(regexp = "\\d{18}") String accountNumber,
        String iban,
        String maskedCardNumber
) {
}
