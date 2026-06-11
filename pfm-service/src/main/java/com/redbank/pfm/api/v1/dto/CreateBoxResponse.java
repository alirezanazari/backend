package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.pfm.BoxSummary;

public record CreateBoxResponse(
        BoxSummary box,
        AccountSummary account
) {
}
