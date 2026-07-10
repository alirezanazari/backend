package com.redbank.account.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CloseAccountRequest(
        String boxId
) {
}
