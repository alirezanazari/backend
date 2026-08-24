package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BoxTransferRequest (@NotBlank String boxId,
                                  @NotNull BigDecimal amount,
                                  @NotBlank String idempotencyKey,
                                  String description){
}


