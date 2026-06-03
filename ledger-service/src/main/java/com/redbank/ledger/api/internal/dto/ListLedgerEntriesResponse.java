package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.common.PageResponse;
import java.util.List;

public record ListLedgerEntriesResponse(
        List<LedgerEntrySummary> entries,
        PageResponse page
) {
}
