package com.redbank.pfm.api.v1.dto;

import jakarta.validation.constraints.Size;

public record ListBoxPresetsRequest(
        @Size(max = 20) String language
) {
}
