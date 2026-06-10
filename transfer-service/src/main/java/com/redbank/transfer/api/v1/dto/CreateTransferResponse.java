package com.redbank.transfer.api.v1.dto;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.transfer.TransferDestinationSummary;
import com.redbank.contracts.transfer.TransferStatus;
import java.time.Instant;

public record CreateTransferResponse(
        String transferId,
        TransferStatus status,
        String ledgerTransactionId,
        TransferDestinationSummary destination,
        Money amount,
        Instant createdAt
) {
}
