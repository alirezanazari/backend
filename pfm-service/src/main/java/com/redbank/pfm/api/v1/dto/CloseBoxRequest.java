package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseBoxRequest (@NotBlank @Size(max = 120) String title,
                              String boxId,
                              String presetId,
                              @Valid Image image,
                              @Size(min = 3, max = 3) String currency
) {
}

