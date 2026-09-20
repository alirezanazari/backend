package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.transfer.TransferStatus;

import java.time.Instant;

public record AccountTransferResponse(
        java.util.UUID transferId,
        TransferStatus status,
        Instant createdAt
){
}

