package com.redbank.iam.api.v1.dto;

import com.redbank.contracts.common.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 40) String nationalId,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotBlank @Size(min = 8, max = 200) String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotNull @Valid Location location,
        @NotBlank @Size(max = 80) String jobId
) {
}
