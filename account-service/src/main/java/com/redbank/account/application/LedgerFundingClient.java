package com.redbank.account.application;

import com.redbank.contracts.common.Money;

public interface LedgerFundingClient {
    public LedgerFundingResult fund(String fundingId,
                String accountNumber,
                Money amount);


}
