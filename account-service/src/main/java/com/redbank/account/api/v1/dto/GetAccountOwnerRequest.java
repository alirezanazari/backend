package com.redbank.account.api.v1.dto;

import com.redbank.contracts.transfer.DestinationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GetAccountOwnerRequest(
        @NotNull DestinationType destinationType,
        @Pattern(regexp = "\\d{18}", message = "must be exactly 18 numeric digits") String accountNumber,
        @Size(max = 34) String iban,
        @Size(max = 32) String cardNumber
) {
}
