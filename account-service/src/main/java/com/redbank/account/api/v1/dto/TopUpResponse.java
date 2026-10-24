package com.redbank.account.api.v1.dto;
import com.redbank.contracts.common.Money;

public record TopUpResponse (
        Money amount,
        String currency,
        String transactionId,
        String status
) {
}