package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.common.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ListLedgerEntriesRequest(
        @NotBlank @Pattern(regexp = "\\d{18}") String externalAccountNumber,
        LocalDate fromDate,
        LocalDate toDate,
        @Valid PageRequest page
) {
}
