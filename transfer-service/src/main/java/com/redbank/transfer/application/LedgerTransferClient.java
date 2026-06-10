package com.redbank.transfer.application;

import com.redbank.contracts.common.Money;

public interface LedgerTransferClient {
    LedgerTransferResult post(
            String transferId,
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    );
}
