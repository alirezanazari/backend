package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.common.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ListPfmTransactionsRequest(
        @Pattern(regexp = "\\d{18}") String accountNumber,
        String boxId,
        LocalDate fromDate,
        LocalDate toDate,
        @Valid PageRequest page
) {
}
