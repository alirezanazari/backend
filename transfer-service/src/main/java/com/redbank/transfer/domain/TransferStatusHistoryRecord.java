package com.redbank.transfer.domain;

import com.redbank.contracts.transfer.TransferStatus;
import java.time.Instant;
import java.util.UUID;

public record TransferStatusHistoryRecord(
        UUID id,
        UUID transferId,
        TransferStatus previousStatus,
        TransferStatus newStatus,
        String reason,
        Instant createdAt
) {
}
