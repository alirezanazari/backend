package com.redbank.transfer.domain;

import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.contracts.transfer.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferRecord(
        UUID id,
        String requestId,
        UUID userId,
        UUID profileId,
        DestinationType sourceType,
        String sourceReference,
        String sourceAccountNumber,
        DestinationType destinationType,
        String destinationReference,
        String destinationAccountNumber,
        String destinationDisplayName,
        String destinationAccountOrCardNumber,
        BigDecimal amount,
        String currency,
        TransferMethod method,
        TransferStatus status,
        String ledgerTransactionId,
        String description,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public TransferRecord withStatus(
            TransferStatus newStatus,
            String newLedgerTransactionId,
            String newFailureReason,
            Instant newUpdatedAt
    ) {
        return new TransferRecord(
                id,
                requestId,
                userId,
                profileId,
                sourceType,
                sourceReference,
                sourceAccountNumber,
                destinationType,
                destinationReference,
                destinationAccountNumber,
                destinationDisplayName,
                destinationAccountOrCardNumber,
                amount,
                currency,
                method,
                newStatus,
                newLedgerTransactionId,
                description,
                newFailureReason,
                createdAt,
                newUpdatedAt
        );
    }
}
