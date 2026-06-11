package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.PageResponse;
import com.redbank.contracts.pfm.PfmTransactionSummary;
import java.util.List;

public record ListPfmTransactionsResponse(
        List<PfmTransactionSummary> transactions,
        PageResponse page
) {
}
