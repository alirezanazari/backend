package com.redbank.contracts.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequest(
        @Min(0) Integer page,
        @Min(1) @Max(200) Integer size
) {
}
