package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.Money;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountTransferRequest (
        @NotBlank String boxId,
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotNull @Valid Money amount,
        @Size(max = 500) String description
){
}








