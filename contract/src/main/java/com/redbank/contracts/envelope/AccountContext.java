package com.redbank.contracts.envelope;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountContext(
        @Pattern(regexp = "\\d{18}", message = "must be exactly 18 numeric digits") String accountNumber,
        @Size(max = 80) String profileId
) {
}
