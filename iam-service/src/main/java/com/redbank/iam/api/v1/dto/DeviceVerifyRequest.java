package com.redbank.iam.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceVerifyRequest(
        @NotBlank @Size(max = 80) String challengeId,
        @NotBlank @Pattern(regexp = "\\d{6}") String otpCode
) {
}
