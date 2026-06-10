package com.redbank.transfer.api.v1.dto;

public record RecentTransferDestination(
        String destinationName,
        String destinationAccountOrCardNumber
) {
}
