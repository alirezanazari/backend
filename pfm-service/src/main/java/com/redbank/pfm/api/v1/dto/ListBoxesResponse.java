package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxSummary;
import java.util.List;

public record ListBoxesResponse(
        List<BoxSummary> boxes
) {
}
