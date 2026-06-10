package com.redbank.transfer.application;

import com.redbank.contracts.transfer.TransferDestinationSummary;

public record ResolvedTransferParties(
        String sourceReference,
        String sourceAccountNumber,
        String destinationReference,
        String destinationAccountNumber,
        TransferDestinationSummary destination
) {
}
