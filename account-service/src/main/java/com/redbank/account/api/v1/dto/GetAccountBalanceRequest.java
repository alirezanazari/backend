package com.redbank.account.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GetAccountBalanceRequest(
        @NotBlank @Pattern(regexp = "\\d{18}", message = "must be exactly 18 numeric digits") String accountNumber
) {
}
