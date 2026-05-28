package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import java.util.List;

public record ListAccountsRequest(
        List<AccountType> types,
        List<AccountStatus> statuses,
        Boolean includeBalance
) {
}
