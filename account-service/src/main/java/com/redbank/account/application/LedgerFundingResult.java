package com.redbank.account.application;

import java.time.Instant;

public record LedgerFundingResult (String ledgerTransactionId,
                                   String status,
                                   Instant createdAt){
}
