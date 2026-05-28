package com.redbank.account.api.v1.dto;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
        @NotBlank @Size(max = 80) String jobId,
        @Size(max = 120) String profileDisplayName,
        @Valid Image profileImage,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter uppercase currency code") String currency
) {
}
