package com.redbank.account.api.v1.dto;

import com.redbank.contracts.common.Money;
import jakarta.validation.constraints.Pattern;

public record TopUpRequest (@Pattern(regexp = "\\d{18}", message = "must be exactly 18 numeric digits") String accountNumber,
                           Money amount){
}
