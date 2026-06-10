package com.redbank.transfer.api.v1.dto;

import com.redbank.contracts.transfer.DestinationType;

public record ListTransferMethodsRequest(
        DestinationType sourceType,
        DestinationType destinationType
) {
}
