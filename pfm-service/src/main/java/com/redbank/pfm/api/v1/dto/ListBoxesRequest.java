package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxStatus;
import java.util.List;

public record ListBoxesRequest(
        List<BoxStatus> statuses,
        Boolean includeBalance
) {
}
