package com.redbank.contracts.common;

import jakarta.validation.constraints.Size;

public record Image(
        String lightUrl,
        String darkUrl,
        @Size(max = 120) String alt
) {
}
