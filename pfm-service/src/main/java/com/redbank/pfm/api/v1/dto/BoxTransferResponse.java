package com.redbank.pfm.api.v1.dto;
import com.redbank.contracts.transfer.TransferStatus;
import java.time.Instant;
import java.util.UUID;
public record BoxTransferResponse(UUID transferId,
                                  TransferStatus status,
                                  Instant createdAt) {

}
