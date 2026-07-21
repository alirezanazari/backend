package com.redbank.kyc.api.v1.dto;

import jakarta.validation.constraints.NotBlank;

public record SetPhoneRequest(
        @NotBlank String phoneNumber
) {
}
