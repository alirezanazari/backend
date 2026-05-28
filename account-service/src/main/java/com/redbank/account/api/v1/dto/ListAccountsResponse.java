package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.AccountSummary;
import java.util.List;

public record ListAccountsResponse(
        List<AccountSummary> accounts
) {
}
