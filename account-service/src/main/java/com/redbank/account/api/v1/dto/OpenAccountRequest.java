package com.redbank.account.api.v1.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @Pattern(regexp = "\\d{18}", message = "must be exactly 18 numeric digits") String accountNumber,
        @Size(max = 500) String reasonText
) {
}
