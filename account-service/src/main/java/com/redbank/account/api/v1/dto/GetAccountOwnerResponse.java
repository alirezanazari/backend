package com.redbank.account.api.v1.dto;

import com.redbank.contracts.transfer.TransferDestinationSummary;

public record GetAccountOwnerResponse(
        TransferDestinationSummary destination
) {
}
