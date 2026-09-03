package com.redbank.pfm.application;
import com.redbank.contracts.common.Money;
public interface PfmTransferClient {
    PfmTransferResult transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    );
}
