package com.redbank.transfer.application;

import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import java.util.UUID;

public interface TransferPartyClient {
    ResolvedTransferParties resolve(CreateTransferRequest request, UUID userId, UUID profileId);
}
