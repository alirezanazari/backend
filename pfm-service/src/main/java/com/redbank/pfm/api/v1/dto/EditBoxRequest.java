package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditBoxRequest(
        @NotBlank String boxId,
        @Size(max = 120) String title,
        String presetId,
        @Valid Image image
) {
}
