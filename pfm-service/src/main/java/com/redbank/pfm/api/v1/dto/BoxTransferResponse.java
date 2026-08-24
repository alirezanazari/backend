package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxStatus;

import java.time.Instant;
import java.util.UUID;

public record BoxTransferResponse(UUID transferId,
                                  BoxStatus status,
                                  Instant createdAt) {

}
