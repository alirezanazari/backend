package com.redbank.pfm.application;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;

public interface PfmTransferClient {
    PfmTransferResult transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    );
}
