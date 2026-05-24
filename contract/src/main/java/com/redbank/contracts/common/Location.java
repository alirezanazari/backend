package com.redbank.contracts.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Location(
        @Size(max = 2) String countryCode,
        @NotBlank @Size(max = 80) String province,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 500) String address,
        @Size(max = 20) String postalCode
) {
}
