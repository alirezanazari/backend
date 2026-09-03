package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record BoxTransferRequest (@NotBlank String boxId,
                                  @NotNull Money amount,
                                  @NotBlank String idempotencyKey,
                                  String description){
}