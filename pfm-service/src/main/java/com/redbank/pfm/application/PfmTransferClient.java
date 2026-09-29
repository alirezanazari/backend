package com.redbank.pfm.application;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.RequestContext;

public interface PfmTransferClient {
    PfmTransferResult transfer(
            String authorizationHeader,
            RequestContext context,
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey,
            String description
    );
}
