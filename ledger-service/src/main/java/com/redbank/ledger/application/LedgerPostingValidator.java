package com.redbank.ledger.application;

import com.redbank.contracts.ledger.LedgerDirection;
import com.redbank.ledger.domain.LedgerEntryRecord;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class LedgerPostingValidator {
    void validate(List<LedgerEntryRecord> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new LedgerBusinessRuleException("Ledger transaction must have entries");
        }

        Set<String> currencies = new HashSet<>();
        Set<UUID> transactionIds = new HashSet<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        int debitCount = 0;
        int creditCount = 0;

        for (LedgerEntryRecord entry : entries) {
            validateEntry(entry);
            currencies.add(entry.currency());
            transactionIds.add(entry.ledgerTransactionId());
            if (entry.direction() == LedgerDirection.DEBIT) {
                debitTotal = debitTotal.add(entry.amount());
                debitCount++;
            } else {
                creditTotal = creditTotal.add(entry.amount());
                creditCount++;
            }
        }

        if (transactionIds.size() != 1) {
            throw new LedgerBusinessRuleException("Ledger entries must belong to the same transaction");
        }
        if (currencies.size() != 1) {
            throw new LedgerBusinessRuleException("Ledger transaction cannot mix currencies");
        }
        if (debitCount == 0 || creditCount == 0) {
            throw new LedgerBusinessRuleException("Ledger transaction must include debit and credit entries");
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new LedgerBusinessRuleException("Ledger debits and credits must balance");
        }
    }

    private void validateEntry(LedgerEntryRecord entry) {
        if (entry == null) {
            throw new LedgerBusinessRuleException("Ledger entry is required");
        }
        if (entry.ledgerTransactionId() == null) {
            throw new LedgerBusinessRuleException("Ledger entry transaction ID is required");
        }
        if (entry.ledgerAccountId() == null) {
            throw new LedgerBusinessRuleException("Ledger entry account ID is required");
        }
        if (entry.direction() == null) {
            throw new LedgerBusinessRuleException("Ledger entry direction is required");
        }
        if (entry.currency() == null || entry.currency().isBlank()) {
            throw new LedgerBusinessRuleException("Ledger entry currency is required");
        }
        if (entry.amount() == null || entry.amount().signum() <= 0) {
            throw new LedgerBusinessRuleException("Ledger entry amount must be greater than zero");
        }
    }
}
