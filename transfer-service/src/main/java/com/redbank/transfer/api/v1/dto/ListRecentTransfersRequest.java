package com.redbank.transfer.api.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ListRecentTransfersRequest(
        @Min(1) @Max(50) Integer limit
) {
}
