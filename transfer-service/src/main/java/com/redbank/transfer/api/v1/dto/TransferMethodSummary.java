package com.redbank.transfer.api.v1.dto;

import com.redbank.contracts.transfer.TransferMethod;

public record TransferMethodSummary(
        TransferMethod method,
        Boolean enabled,
        String title
) {
}
