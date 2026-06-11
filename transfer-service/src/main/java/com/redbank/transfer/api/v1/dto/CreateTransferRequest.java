package com.redbank.transfer.api.v1.dto;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotNull TransferMethod method,
        @NotNull DestinationType sourceType,
        @Pattern(regexp = "\\d{18}") String sourceAccountNumber,
        @Size(max = 80) String sourceCardNumber,
        @NotNull DestinationType destinationType,
        @Pattern(regexp = "\\d{18}") String destinationAccountNumber,
        @Size(max = 34) String destinationIban,
        @Size(max = 80) String destinationCardNumber,
        @NotNull @Valid Money amount,
        @Size(max = 500) String description
) {
}
