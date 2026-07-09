package com.redbank.contracts.pfm;

import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Image;

public record BoxSummary(
        String boxId,
        String accountNumber,
        String title,
        Image image,
        BoxStatus status,
        BalanceSummary balance
) {
}
