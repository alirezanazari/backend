package com.redbank.card.api.v1.dto;

import com.redbank.contracts.common.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderCardRequest(
        @NotBlank @Pattern(regexp = "\\d{18}") String accountNumber,
        @Valid Location deliveryLocation,
        @Size(max = 500) String reason
) {
}
