package com.redbank.pfm.application;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.transfer.TransferStatus;

public record PfmTransferResult (
        String transferId,
        TransferStatus status,
        String ledgerTransactionId
){
}