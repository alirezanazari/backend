package com.redbank.contracts.account;

import com.redbank.contracts.common.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BalanceSummary(
        @NotNull @Valid Money availableBalance,
        @NotNull @Valid Money currentBalance,
        @NotNull Instant updatedAt
) {
}
