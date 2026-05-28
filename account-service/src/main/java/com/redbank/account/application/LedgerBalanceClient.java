package com.redbank.account.application;

import com.redbank.contracts.account.BalanceSummary;
import java.math.BigDecimal;

public interface LedgerBalanceClient {
    BalanceSummary getBalance(String accountNumber, String currency);

    default boolean isZero(String accountNumber, String currency) {
        BalanceSummary balance = getBalance(accountNumber, currency);
        return balance.availableBalance().amount().compareTo(BigDecimal.ZERO) == 0
                && balance.currentBalance().amount().compareTo(BigDecimal.ZERO) == 0;
    }
}
