package com.redbank.pfm.application;

import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.common.PageRequest;
import com.redbank.contracts.common.PageResponse;
import com.redbank.contracts.ledger.LedgerDirection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerPfmClient {
    void createAccount(String accountNumber, UUID userId, String currency);

    BalanceSummary getBalance(String accountNumber);

    LedgerEntries listEntries(
            String accountNumber,
            LocalDate fromDate,
            LocalDate toDate,
            PageRequest page
    );

    record LedgerEntries(
            List<LedgerEntry> entries,
            PageResponse page
    ) {
    }

    record LedgerEntry(
            String entryId,
            String ledgerTransactionId,
            LedgerDirection direction,
            Money amount,
            Instant createdAt
    ) {
    }
}
