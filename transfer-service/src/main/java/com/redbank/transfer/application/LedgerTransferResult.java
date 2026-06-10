package com.redbank.transfer.application;

public record LedgerTransferResult(
        String ledgerTransactionId,
        String status
) {
}
