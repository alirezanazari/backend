package com.redbank.account.application;

import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Money;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redbank.ledger", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalZeroLedgerBalanceClient implements LedgerBalanceClient {
    private final Clock clock;

    public LocalZeroLedgerBalanceClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public BalanceSummary getBalance(String accountNumber, String currency) {
        Money zero = new Money(BigDecimal.ZERO, currency);
        return new BalanceSummary(zero, zero, clock.instant());
    }
}
