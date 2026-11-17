package com.redbank.account.api.v1.dto;
import com.redbank.contracts.common.Money;

import java.time.Instant;

public record TopUpResponse (
        String accountNumber,
        Money amount,
        String currency,
        String transactionId,
        String status,
        Instant createdAt
) {
}