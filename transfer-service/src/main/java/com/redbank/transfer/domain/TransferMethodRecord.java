package com.redbank.transfer.domain;

import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;

public record TransferMethodRecord(
        TransferMethod method,
        DestinationType sourceType,
        DestinationType destinationType,
        String title,
        boolean enabled,
        int sortOrder
) {
}
