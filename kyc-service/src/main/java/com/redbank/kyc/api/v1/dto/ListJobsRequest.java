package com.redbank.kyc.api.v1.dto;

import jakarta.validation.constraints.Size;

public record ListJobsRequest(
        @Size(max = 16) String language
) {
}
